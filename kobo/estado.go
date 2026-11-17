package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"syscall"
	"time"
)

// ---- Lo que el programa recuerda en el Kobo (.adds/seshat/enviado.json)

// contador: el tiempo leído de una entrada de la base de datos del Kobo (un archivo o un libro de la tienda: su ContentID) tal como lo
// lleva este programa. El Kobo apunta el total en segundos (TimeSpentReading), pero puede volver a cero (el libro se borra y se vuelve a
// copiar, el Kobo se restaura): entonces lo que llevaba se guarda en [Base] y no se pierde. [Dias]: lo leído cada día que ha visto este
// programa (todo, no solo lo que falta por subir: en la cuenta se pone al día, nunca se suma dos veces). [K]: el libro (su kid) al que
// pertenece; si la entrada desaparece (se borra la muestra y queda el comprado), su tiempo sigue contando en su libro.
type contador struct {
	K    string           `json:"k"`
	S    int64            `json:"s"`
	Base int64            `json:"base"`
	Dias map[string]int64 `json:"dias,omitempty"`
}

type estadoLocal struct {
	Uid       string               `json:"uid"`
	BaseDatos string               `json:"baseDatos"`
	Huellas   map[string]string    `json:"huellas"`
	Entradas  map[string]*contador `json:"entradas"` // por ContentID
}

// cargaEstado lee el estado; si no está o está roto, empieza de cero (lo de formatos anteriores que no entiende, también).
func cargaEstado(ruta string) *estadoLocal {
	e := &estadoLocal{}
	if b, err := os.ReadFile(ruta); err == nil {
		if json.Unmarshal(b, e) != nil {
			e = &estadoLocal{}
		}
	}
	if e.Huellas == nil {
		e.Huellas = map[string]string{}
	}
	if e.Entradas == nil {
		e.Entradas = map[string]*contador{}
	}
	return e
}

// deCuenta: lo enviado se recuerda por cuenta. Con otra cuenta (el Kobo se ha preparado de nuevo), todo vuelve a mirarse.
func (e *estadoLocal) deCuenta(uid, baseDatos string) {
	if e.Uid != uid || e.BaseDatos != baseDatos {
		e.Uid, e.BaseDatos, e.Huellas = uid, baseDatos, map[string]string{}
	}
}

func (e *estadoLocal) guarda(ruta string) error {
	b, err := json.Marshal(e)
	if err != nil {
		return err
	}
	return escribeSeguro(ruta, b, 0o644)
}

// Días que se recuerdan (los de antes ya están en la cuenta; el archivo no crece sin fin)
const diasQueSeRecuerdan = 400

// registra pone al día los contadores con lo que dice el Kobo de cada entrada (sin juntar: [entradas] tal como salen de la base de
// datos). Lo nuevo desde la última vez va al día de su última lectura (si este programa está vigilando, eso es el día en que se lee).
func (e *estadoLocal) registra(entradas []LibroKobo, zona *time.Location, ahora time.Time) {
	for _, l := range entradas {
		if l.ID == "" {
			continue
		}
		c := e.Entradas[l.ID]
		if c == nil {
			// La primera vez no se sabe en qué días se leyó lo de antes: solo cuenta para el total
			e.Entradas[l.ID] = &contador{K: kid(claveDe(l.Titulo, l.Autor)), S: l.Segundos}
			continue
		}
		c.K = kid(claveDe(l.Titulo, l.Autor))
		delta := l.Segundos - c.S
		if l.Segundos < c.S {
			c.Base += c.S * 1000 // el Kobo ha vuelto a cero: lo de antes se queda
			delta = l.Segundos
		}
		if delta > 0 {
			dia := ahora
			if !l.UltimaLectura.IsZero() && !l.UltimaLectura.After(ahora) {
				dia = l.UltimaLectura
			}
			if c.Dias == nil {
				c.Dias = map[string]int64{}
			}
			c.Dias[dia.In(zona).Format("2006-01-02")] += delta * 1000
		}
		c.S = l.Segundos
	}
	limite := ahora.In(zona).AddDate(0, 0, -diasQueSeRecuerdan).Format("2006-01-02")
	for _, c := range e.Entradas {
		for d := range c.Dias {
			if d < limite {
				delete(c.Dias, d)
			}
		}
	}
}

// tiempos pone en cada libro (ya juntados: uno por kid de su clave) su tiempo según este programa: el total en ms y lo leído cada día,
// sumando todas sus entradas, también las que ya no están en el Kobo.
func (e *estadoLocal) tiempos(libros []LibroKobo) {
	for i := range libros {
		l := &libros[i]
		k := kid(claveDe(l.Titulo, l.Autor))
		l.PorDias, l.Total, l.Dias = true, 0, map[string]int64{}
		for _, c := range e.Entradas {
			if c.K != k {
				continue
			}
			l.Total += c.Base + c.S*1000
			for d, v := range c.Dias {
				l.Dias[d] += v
			}
		}
	}
}

// ---- Archivos

// escribeSeguro escribe en un archivo aparte, lo pasa al disco y lo pone en su sitio: un corte a mitad (se apaga, se enchufa por USB)
// deja el archivo de antes, nunca uno a medias (en FAT, uno truncado o con trozos sin escribir).
func escribeSeguro(ruta string, datos []byte, modo os.FileMode) error {
	if !montado(ruta) {
		return fmt.Errorf("la memoria del Kobo no está (¿enchufado por USB?)")
	}
	t := ruta + ".nuevo"
	f, err := os.OpenFile(t, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, modo)
	if err != nil {
		return err
	}
	if _, err = f.Write(datos); err == nil {
		err = f.Sync()
	}
	if e := f.Close(); err == nil {
		err = e
	}
	if err != nil {
		_ = os.Remove(t)
		return err
	}
	return os.Rename(t, ruta)
}

// La memoria del Kobo (donde están los libros y .adds). Con el Kobo enchufado por USB no está montada: escribir ahí lo haría en la
// carpeta vacía de debajo, donde nadie lo ve.
var memoriaKobo = "/mnt/onboard"

func montado(ruta string) bool {
	if !strings.HasPrefix(ruta, memoriaKobo+"/") {
		return true
	}
	b, err := os.ReadFile("/proc/mounts")
	if err != nil {
		return true
	}
	for _, l := range strings.Split(string(b), "\n") {
		if f := strings.Fields(l); len(f) > 1 && f[1] == memoriaKobo {
			return true
		}
	}
	return false
}

// copiaEjecutable deja [de] en [a] (en /tmp) si no está ya igual, para ejecutarlo desde ahí: un programa que corre desde la memoria
// del Kobo la deja ocupada y no se puede enchufar por USB.
func copiaEjecutable(de, a string) error {
	sd, err := os.Stat(de)
	if err != nil {
		return err
	}
	if sa, err := os.Stat(a); err == nil && sa.Size() == sd.Size() && sa.ModTime().Equal(sd.ModTime()) {
		return nil
	}
	t := fmt.Sprintf("%s.%d", a, os.Getpid())
	if err := copiaArchivo(de, t); err != nil {
		_ = os.Remove(t)
		return err
	}
	_ = os.Chmod(t, 0o755)
	_ = os.Chtimes(t, sd.ModTime(), sd.ModTime())
	return os.Rename(t, a)
}

// copiaBD copia la base de datos del Kobo (con sus -wal y -shm) a [dir] de forma coherente: si el Kobo la ha cambiado mientras se
// copiaba (lo de la base y lo del -wal serían de momentos distintos), se vuelve a copiar.
func copiaBD(ruta, dir string) (string, error) {
	copia := filepath.Join(dir, "KoboReader.sqlite")
	for intento := 0; ; intento++ {
		antes := huellaArchivos(ruta)
		if err := copiaArchivo(ruta, copia); err != nil {
			return "", err
		}
		for _, ext := range []string{"-wal", "-shm"} {
			_ = os.Remove(copia + ext)
			if _, err := os.Stat(ruta + ext); err == nil {
				_ = copiaArchivo(ruta+ext, copia+ext)
			}
		}
		if huellaArchivos(ruta) == antes || intento >= 3 {
			return copia, nil
		}
		time.Sleep(300 * time.Millisecond)
	}
}

// ---- Cerrojos (flock: si el proceso muere, el sistema los suelta solo; no dependen de la hora)

var errOcupado = errors.New("ya se está enviando")

// cerrojo intenta coger el cerrojo [nombre] (en /tmp) sin esperar. Devuelve con qué soltarlo, o errOcupado.
func cerrojo(nombre string) (func(), error) {
	f, err := os.OpenFile(filepath.Join(os.TempDir(), nombre), os.O_CREATE|os.O_RDWR, 0o644)
	if err != nil {
		return nil, err
	}
	if err := syscall.Flock(int(f.Fd()), syscall.LOCK_EX|syscall.LOCK_NB); err != nil {
		f.Close()
		if errors.Is(err, syscall.EWOULDBLOCK) {
			return nil, errOcupado
		}
		return nil, err
	}
	return func() { _ = syscall.Flock(int(f.Fd()), syscall.LOCK_UN); f.Close() }, nil
}

// conCerrojo: una sola vez a la vez (el Wi-Fi, el vigilante y el menú pueden pedirlo juntos). Si ya hay otro, errOcupado.
func conCerrojo(f func() error) error {
	soltar, err := cerrojo("seshat-kobo.cerrojo")
	if err != nil {
		return err
	}
	defer soltar()
	return f()
}
