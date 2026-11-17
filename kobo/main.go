// seshat-kobo: manda lo leído en el Kobo (los libros, por dónde vas, si los has terminado y el tiempo leído) a la cuenta de la
// familia, para Seshat y Jonsu. Lee una copia de la base de datos del Kobo (no la toca) y sube solo lo que es más nuevo que lo que
// ya hay en la cuenta.
//
//	seshat-kobo envia     manda lo que haya cambiado (lo de «Enviar a Seshat» del menú)
//	seshat-kobo espera    espera a que haya internet (hasta 3 minutos) y manda (lo que se lanza al conectar el Wi-Fi)
//	seshat-kobo prueba    dice qué mandaría, sin mandar nada
//	seshat-kobo vigila    se queda esperando: cada vez que el Kobo tiene internet (y cada media hora mientras lo tenga), manda
//
// Opciones: -cuenta <archivo> (por defecto /mnt/onboard/.adds/seshat/cuenta.json), -bd <KoboReader.sqlite>, -registro <archivo>.
package main

import (
	"crypto/sha1"
	"encoding/hex"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
	_ "time/tzdata" // (el Kobo no trae las zonas horarias)

	_ "seshat-kobo/raices"
)

const version = "1.0.0"

var (
	dirSeshat = "/mnt/onboard/.adds/seshat"
	fCuenta   = flag.String("cuenta", filepath.Join(dirSeshat, "cuenta.json"), "la cuenta (la deja Seshat al preparar el Kobo)")
	fBD       = flag.String("bd", "/mnt/onboard/.kobo/KoboReader.sqlite", "la base de datos del Kobo")
	fRegistro = flag.String("registro", filepath.Join(dirSeshat, "registro.txt"), "dónde se apunta lo que pasa")
	fEnviado  = flag.String("enviado", filepath.Join(dirSeshat, "enviado.json"), "lo ya enviado (para no repetir)")
)

func main() {
	flag.Parse()
	orden := flag.Arg(0)
	if orden == "" {
		orden = "envia"
	}
	abreRegistro(*fRegistro)
	var err error
	switch orden {
	case "envia":
		err = conCerrojo(func() error { return envia(false) })
	case "espera":
		err = conCerrojo(func() error {
			if !esperaRed(3 * time.Minute) {
				log.Print("sin internet en 3 minutos: no se manda nada")
				return nil
			}
			return envia(false)
		})
	case "vigila":
		vigila()
	case "prueba":
		err = envia(true)
	case "version":
		fmt.Println(version)
	default:
		err = fmt.Errorf("no sé qué es «%s» (envia, espera, prueba)", orden)
	}
	if err != nil {
		log.Print("ERROR: ", err)
		os.Exit(1)
	}
}

func envia(soloMirar bool) error {
	libros, err := leeKobo(*fBD)
	if err != nil {
		return err
	}
	// El índice de NickelInkSync (la posición de cada libro), si está instalado
	if !soloMirar {
		raiz := filepath.Dir(filepath.Dir(*fBD))
		if c, n, err := actualizaIndiceNIS(*fBD, raiz, filepath.Join(dirSeshat, "huellas.json")); err != nil {
			log.Print("índice de NickelInkSync: ", err)
		} else if c {
			log.Printf("posiciones: %d libros del Kobo en el índice de NickelInkSync", n)
		}
	}
	// Lo que ya se envió tal cual no se vuelve a mirar en la cuenta (menos tráfico y más rápido)
	enviado := map[string]string{}
	if b, err := os.ReadFile(*fEnviado); err == nil {
		_ = json.Unmarshal(b, &enviado)
	}
	// La huella de cada kepub copiado (para que Seshat sepa que es el mismo archivo); se guarda (no se relee si no cambia)
	raizKobo := filepath.Dir(filepath.Dir(*fBD))
	cacheHuellas := map[string]string{}
	if b, err := os.ReadFile(filepath.Join(dirSeshat, "huellas.json")); err == nil {
		_ = json.Unmarshal(b, &cacheHuellas)
	}
	for i := range libros {
		if libros[i].Marca == "" {
			continue
		}
		ruta := rutaDeContenido(libros[i].ID, raizKobo)
		st, err := os.Stat(ruta)
		if err != nil {
			libros[i].Marca = ""
			continue
		}
		k := fmt.Sprintf("%s|%d|%d", ruta, st.Size(), st.ModTime().Unix())
		h := cacheHuellas[k]
		if h == "" {
			h, _ = huellaKOReader(ruta)
		}
		libros[i].HuellaKepub = h
	}
	var cambiados []LibroKobo
	huellas := map[string]string{}
	for _, l := range libros {
		k := kid(claveDe(l.Titulo, l.Autor))
		h := huella(l)
		huellas[k] = h
		if soloMirar || enviado[k] != h {
			cambiados = append(cambiados, l)
		}
	}
	if len(cambiados) == 0 {
		log.Printf("nada nuevo (%d libros en el Kobo)", len(libros))
		return nil
	}
	c, err := cargaCuenta(*fCuenta)
	if err != nil {
		return err
	}
	if err := c.entra(); err != nil {
		return err
	}
	// Todos los libros de la cuenta de una vez (para encontrar el que es aunque el Kobo escriba el autor de otra manera)
	todos := map[string]Nodo{}
	if n, err := c.lee("/users/" + c.Uid + "/familia/libros"); err != nil {
		return err
	} else {
		for ref, v := range n {
			if m, ok := v.(map[string]any); ok {
				todos[ref] = Nodo(m)
			}
		}
	}
	remotos := map[string]Remoto{}
	for i := range cambiados {
		k := buscaKid(cambiados[i], todos)
		cambiados[i].Kid = k
		lib := todos[k]
		tie, err := c.lee("/users/" + c.Uid + "/familia/tiempo/" + k + "~" + c.Dispositivo)
		if err != nil {
			return err
		}
		remotos[k] = Remoto{Libro: lib, Tiempo: tie}
	}
	p := planea(cambiados, remotos, time.Now(), c.zona(), c.Dispositivo)
	if soloMirar {
		for _, r := range p.Resumen {
			log.Print("mandaría: ", r)
		}
		log.Printf("%d cambios en la cuenta (no se ha mandado nada)", len(p.Cambios))
		return nil
	}
	if len(p.Cambios) > 0 {
		if err := c.sube(p.Cambios); err != nil {
			return err
		}
	}
	for _, r := range p.Resumen {
		log.Print("enviado: ", r)
	}
	if len(p.Resumen) == 0 {
		log.Print("la cuenta ya tenía lo del Kobo (o algo más nuevo)")
	}
	for _, l := range cambiados {
		k := kid(claveDe(l.Titulo, l.Autor))
		enviado[k] = huellas[k]
	}
	if b, err := json.Marshal(enviado); err == nil {
		_ = os.WriteFile(*fEnviado, b, 0o644)
	}
	return nil
}

func huella(l LibroKobo) string {
	h := sha1.Sum([]byte(fmt.Sprintf("%s|%s|%.2f|%d|%d|%d|%d|%s|%s", l.Titulo, l.Autor, l.Porcentaje, l.Estado, ms(l.UltimaLectura), l.Segundos, ms(l.Terminado), l.Marca, l.HuellaKepub)))
	return hex.EncodeToString(h[:8])
}

// vigila se queda en marcha (hasta que se apague el Kobo; dormido no gasta nada porque no corre): cada minuto mira si hay
// conexión y, cuando la hay (al encender el Wi-Fi y luego cada media hora), manda lo que haya cambiado. Si ya hay otro vigilando, sale.
func vigila() {
	pid := filepath.Join(os.TempDir(), "seshat-kobo.pid")
	if b, err := os.ReadFile(pid); err == nil {
		if n, _ := strconv.Atoi(strings.TrimSpace(string(b))); n > 0 && n != os.Getpid() {
			if cmd, err := os.ReadFile(fmt.Sprintf("/proc/%d/cmdline", n)); err == nil && strings.Contains(string(cmd), "seshat-kobo") {
				return
			}
		}
	}
	_ = os.WriteFile(pid, []byte(strconv.Itoa(os.Getpid())), 0o644)
	log.Print("vigilando: mandará solo cada vez que haya Wi-Fi")
	antes := false
	var ultimo time.Time
	var huellaBD string
	for {
		ahora := hayRuta()
		if ahora && (!antes || time.Since(ultimo) > 30*time.Minute) {
			if h := huellaArchivos(*fBD); h != "" && h == huellaBD {
				ultimo = time.Now() // la base de datos del Kobo no ha cambiado: nada que mirar
			} else if h != "" {
				time.Sleep(10 * time.Second) // (la conexión recién hecha tarda un poco en servir)
				if err := conCerrojo(func() error { return envia(false) }); err != nil {
					log.Print("ERROR: ", err, " (se reintenta en un minuto)")
					ahora = false // así se vuelve a intentar
				} else {
					ultimo, huellaBD = time.Now(), h
				}
			}
		}
		antes = ahora
		time.Sleep(time.Minute)
	}
}

// hayRuta: si el Kobo tiene salida a internet (una ruta por defecto que no sea la interna).
func hayRuta() bool {
	b, err := os.ReadFile("/proc/net/route")
	if err != nil {
		return false
	}
	for _, l := range strings.Split(string(b), "\n")[1:] {
		f := strings.Fields(l)
		if len(f) > 2 && f[0] != "lo" && f[1] == "00000000" {
			return true
		}
	}
	return false
}

// huellaArchivos: tamaño y hora de la base de datos y su -wal (si no cambian, el Kobo no ha apuntado nada nuevo). Vacía si no está
// (con el Kobo enchufado por USB, la memoria no está).
func huellaArchivos(ruta string) string {
	st, err := os.Stat(ruta)
	if err != nil {
		return ""
	}
	h := fmt.Sprintf("%d.%d", st.Size(), st.ModTime().UnixNano())
	if w, err := os.Stat(ruta + "-wal"); err == nil {
		h += fmt.Sprintf("|%d.%d", w.Size(), w.ModTime().UnixNano())
	}
	return h
}

// esperaRed espera a que se pueda hablar con la cuenta (el Wi-Fi tarda unos segundos en dar dirección después de encenderse).
func esperaRed(max time.Duration) bool {
	fin := time.Now().Add(max)
	for time.Now().Before(fin) {
		if c, err := net.DialTimeout("tcp", "securetoken.googleapis.com:443", 5*time.Second); err == nil {
			c.Close()
			return true
		}
		time.Sleep(5 * time.Second)
	}
	return false
}

// conCerrojo: una sola vez a la vez (el Wi-Fi y el menú pueden pedirlo juntos). Un cerrojo de hace más de 10 minutos se da por perdido.
func conCerrojo(f func() error) error {
	cerrojo := filepath.Join(os.TempDir(), "seshat-kobo.cerrojo")
	if st, err := os.Stat(cerrojo); err == nil && time.Since(st.ModTime()) > 10*time.Minute {
		_ = os.Remove(cerrojo)
	}
	g, err := os.OpenFile(cerrojo, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o644)
	if err != nil {
		if errors.Is(err, os.ErrExist) {
			log.Print("ya se está enviando")
			return nil
		}
		return err
	}
	g.Close()
	defer os.Remove(cerrojo)
	return f()
}

// abreRegistro apunta en [archivo] (y en la pantalla, si se lanza a mano). El archivo se abre y se cierra en cada línea: así no queda
// nada abierto en la memoria del Kobo (si no, al enchufarlo por USB no podría prestarla). Si pasa de 64 KB, se empieza de nuevo
// guardando el anterior.
func abreRegistro(archivo string) {
	log.SetFlags(0)
	log.SetOutput(io.MultiWriter(os.Stderr, registro(archivo)))
}

type registro string

func (r registro) Write(p []byte) (int, error) {
	archivo := string(r)
	if st, err := os.Stat(archivo); err == nil && st.Size() > 64*1024 {
		_ = os.Rename(archivo, archivo+".1")
	}
	_ = os.MkdirAll(filepath.Dir(archivo), 0o755)
	f, err := os.OpenFile(archivo, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o644)
	if err != nil {
		return len(p), nil // (sin registro se sigue igual)
	}
	defer f.Close()
	_, _ = f.Write(append([]byte(time.Now().Format("2006-01-02 15:04 ")), p...))
	return len(p), nil
}
