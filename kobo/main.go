// seshat-kobo: manda lo leído en el Kobo (los libros, por dónde vas, si los has terminado y el tiempo leído) a la cuenta de la
// familia, para Seshat y Jonsu. Lee una copia de la base de datos del Kobo (no la toca) y sube solo lo que es más nuevo que lo que
// ya hay en la cuenta.
//
//	seshat-kobo envia     manda lo que haya cambiado (lo de «Enviar a Seshat» del menú)
//	seshat-kobo espera    espera a que haya internet (hasta 3 minutos) y manda (lo que se lanza al conectar el Wi-Fi)
//	seshat-kobo prueba    dice qué mandaría, sin mandar nada
//	seshat-kobo vigila    se queda en marcha: apunta lo leído cada día y, cada vez que hay internet (y cada media hora), manda
//	seshat-kobo vincula   solo vincula los kepub con su EPUB (lo que deja SesKobo)
//
// Opciones: -cuenta <archivo> (por defecto /mnt/onboard/.adds/seshat/cuenta.json), -bd <KoboReader.sqlite>, -registro <archivo>.
package main

import (
	"crypto/sha1"
	"encoding/hex"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"syscall"
	"time"
	_ "time/tzdata" // (el Kobo no trae las zonas horarias)

	_ "seshat-kobo/raices"
)

const version = "1.1.0"

var (
	dirSeshat = "/mnt/onboard/.adds/seshat"
	fCuenta   = flag.String("cuenta", filepath.Join(dirSeshat, "cuenta.json"), "la cuenta (la deja Seshat al preparar el Kobo)")
	fBD       = flag.String("bd", "/mnt/onboard/.kobo/KoboReader.sqlite", "la base de datos del Kobo")
	fRegistro = flag.String("registro", filepath.Join(dirSeshat, "registro.txt"), "dónde se apunta lo que pasa")
	fEnviado  = flag.String("enviado", filepath.Join(dirSeshat, "enviado.json"), "lo ya enviado y el tiempo leído por días (el estado)")
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
		// (la espera, fuera del cerrojo: mientras tanto el vigilante puede mandar)
		if !esperaRed(3 * time.Minute) {
			log.Print("sin internet en 3 minutos: no se manda nada")
			break
		}
		err = conCerrojo(func() error { return envia(false) })
	case "vigila":
		vigila()
	case "prueba":
		err = envia(true)
	case "vincula":
		// (solo los kepub vinculados con su EPUB, sin mandar nada: para probarlo)
		var n int
		if n, err = vinculaKepubs(filepath.Dir(filepath.Dir(*fBD))); err == nil {
			log.Printf("%d kepub vinculados", n)
		}
	case "version":
		fmt.Println(version)
	default:
		err = fmt.Errorf("no sé qué es «%s» (envia, espera, prueba, vigila)", orden)
	}
	if errors.Is(err, errOcupado) {
		log.Print("ya se está enviando")
		err = nil
	}
	if err != nil {
		log.Print("ERROR: ", err)
		os.Exit(1)
	}
}

// libros lee el Kobo y junta los que son el mismo libro (la muestra de la tienda y el comprado, el EPUB y el kepub): uno solo por
// clave, el leído más tarde, con el tiempo de todos.
func libros() ([]LibroKobo, error) {
	ls, err := leeKobo(*fBD)
	if err != nil {
		return nil, err
	}
	return agrupa(ls, func(l LibroKobo) string { return kid(claveDe(l.Titulo, l.Autor)) }), nil
}

// apunta pone al día lo leído por días (sin internet: lo hace el vigilante cada vez que el Kobo apunta algo).
func apunta() error {
	ls, err := libros()
	if err != nil {
		return err
	}
	zona := time.UTC
	if c, err := cargaCuenta(*fCuenta); err == nil {
		zona = c.zona()
	}
	e := cargaEstado(*fEnviado)
	e.registra(ls, zona, time.Now())
	return e.guarda(*fEnviado)
}

func envia(soloMirar bool) error {
	ls, err := libros()
	if err != nil {
		return err
	}
	raiz := filepath.Dir(filepath.Dir(*fBD))
	// El índice de NickelInkSync (la posición de cada libro), si está instalado; antes, los kepub que SesKobo ha vinculado con su EPUB
	if !soloMirar {
		if n, err := vinculaKepubs(raiz); err != nil {
			log.Print("vincular kepub: ", err)
		} else if n > 0 {
			log.Printf("posiciones: %d kepub vinculados con su EPUB", n)
		}
		if c, n, err := actualizaIndiceNIS(*fBD, raiz, filepath.Join(dirSeshat, "huellas.json")); err != nil {
			log.Print("índice de NickelInkSync: ", err)
		} else if c {
			log.Printf("posiciones: %d libros del Kobo en el índice de NickelInkSync", n)
		}
	}
	c, err := cargaCuenta(*fCuenta)
	if err != nil {
		return err
	}
	e := cargaEstado(*fEnviado)
	e.deCuenta(c.Uid, c.BaseDatos)
	e.registra(ls, c.zona(), time.Now())
	huellasKepub(ls, raiz)

	// Lo que ya se envió tal cual no se vuelve a mirar en la cuenta (menos tráfico y más rápido)
	var cambiados []LibroKobo
	huellas := map[string]string{}
	for _, l := range ls {
		k := kid(claveDe(l.Titulo, l.Autor))
		h := huella(l)
		huellas[k] = h
		if soloMirar || e.Huellas[k] != h {
			cambiados = append(cambiados, l)
		}
	}
	if len(cambiados) == 0 {
		log.Printf("nada nuevo (%d libros en el Kobo)", len(ls))
		return guardaSiNoMira(e, soloMirar)
	}
	if err := c.entra(); err != nil {
		_ = guardaSiNoMira(e, soloMirar) // (lo leído por días se queda apuntado aunque no se pueda mandar)
		return err
	}
	// Todos los libros de la cuenta de una vez (para encontrar el que es aunque el Kobo escriba el autor de otra manera)
	todos := map[string]Nodo{}
	n, err := c.lee("/users/" + c.Uid + "/familia/libros")
	if err != nil {
		_ = guardaSiNoMira(e, soloMirar)
		return err
	}
	for ref, v := range n {
		if m, ok := v.(map[string]any); ok {
			todos[ref] = Nodo(m)
		}
	}
	remotos := map[string]Remoto{}
	for i := range cambiados {
		k := buscaKid(cambiados[i], todos)
		cambiados[i].Kid = k
		if _, ya := remotos[k]; ya {
			continue
		}
		tie, err := c.lee("/users/" + c.Uid + "/familia/tiempo/" + k + "~" + c.Dispositivo)
		if err != nil {
			_ = guardaSiNoMira(e, soloMirar)
			return err
		}
		remotos[k] = Remoto{Libro: todos[k], Tiempo: tie}
	}
	// La hora de la cuenta (por si el reloj del Kobo va adelantado: nada puede quedar apuntado en el futuro)
	p := planea(cambiados, remotos, c.ahora(), c.zona(), c.Dispositivo)
	if soloMirar {
		for _, r := range p.Resumen {
			log.Print("mandaría: ", r)
		}
		log.Printf("%d cambios en la cuenta (no se ha mandado nada)", len(p.Cambios))
		return nil
	}
	if len(p.Cambios) > 0 {
		if err := c.sube(p.Cambios); err != nil {
			_ = e.guarda(*fEnviado)
			return err
		}
	}
	for _, r := range p.Resumen {
		log.Print("enviado: ", r)
	}
	if len(p.Resumen) == 0 {
		log.Print("la cuenta ya tenía lo del Kobo (o algo más nuevo)")
	}
	// Lo leído por días de estos libros ya está en la cuenta (o lo estaba: planea solo lo suma si el total ha crecido)
	e.subidos(cambiados)
	for _, l := range cambiados {
		k := kid(claveDe(l.Titulo, l.Autor))
		e.Huellas[k] = huellas[k]
	}
	return e.guarda(*fEnviado)
}

func guardaSiNoMira(e *estadoLocal, soloMirar bool) error {
	if soloMirar {
		return nil
	}
	return e.guarda(*fEnviado)
}

// huellasKepub: la huella de cada kepub copiado (para que Seshat sepa que es el mismo archivo); se guarda en huellas.json (no se relee
// un archivo que no ha cambiado).
func huellasKepub(ls []LibroKobo, raiz string) {
	cache := map[string]string{}
	f := filepath.Join(dirSeshat, "huellas.json")
	leeJSON(f, &cache)
	for i := range ls {
		if ls[i].Marca == "" {
			continue
		}
		ruta := rutaDeContenido(ls[i].ID, raiz)
		st, err := os.Stat(ruta)
		if err != nil {
			ls[i].Marca = ""
			continue
		}
		k := fmt.Sprintf("%s|%d|%d", ruta, st.Size(), st.ModTime().Unix())
		h := cache[k]
		if h == "" {
			h, _ = huellaKOReader(ruta)
		}
		ls[i].HuellaKepub = h
	}
}

func huella(l LibroKobo) string {
	dias := make([]string, 0, len(l.Dias))
	for d, v := range l.Dias {
		dias = append(dias, fmt.Sprintf("%s=%d", d, v))
	}
	sort.Strings(dias)
	h := sha1.Sum([]byte(fmt.Sprintf("%s|%s|%.2f|%d|%d|%d|%d|%s|%s|%d|%s", l.Titulo, l.Autor, l.Porcentaje, l.Estado, ms(l.UltimaLectura), l.Segundos,
		ms(l.Terminado), l.Marca, l.HuellaKepub, l.Total, strings.Join(dias, ","))))
	return hex.EncodeToString(h[:8])
}

// vigila se queda en marcha (hasta que se apague el Kobo; dormido no gasta nada porque no corre): cada minuto mira si el Kobo ha
// apuntado algo (y entonces apunta lo leído por días, haya o no internet) y si hay conexión (y entonces, al encender el Wi-Fi y luego
// cada media hora, manda lo que haya cambiado). Uno solo a la vez. Si el programa se actualiza, pasa a la versión nueva.
func vigila() {
	soltar, err := cerrojo("seshat-kobo.vigila")
	if err != nil {
		return // (ya hay otro vigilando)
	}
	defer soltar()
	yo, _ := os.Executable()
	inicio, _ := os.Stat(yo)
	log.Print("vigilando: apunta lo leído cada día y manda cada vez que hay Wi-Fi")
	antes := false
	var ultimo time.Time
	var apuntada, enviada string
	for {
		// ¿Programa nuevo? Se pasa a él (el cerrojo se suelta solo al cambiar de programa)
		if st, err := os.Stat(yo); err == nil && inicio != nil && (st.Size() != inicio.Size() || !st.ModTime().Equal(inicio.ModTime())) {
			log.Print("programa nuevo: el vigilante pasa a él")
			soltar()
			_ = syscall.Exec(yo, os.Args, os.Environ())
			return
		}
		h := huellaArchivos(*fBD)
		if h != "" && h != apuntada {
			if err := conCerrojo(apunta); err == nil {
				apuntada = h
			}
		}
		ahora := hayRuta()
		if ahora && h != "" && (!antes || time.Since(ultimo) > 30*time.Minute) {
			if h == enviada {
				ultimo = time.Now() // la base de datos del Kobo no ha cambiado: nada que mirar
			} else {
				time.Sleep(10 * time.Second) // (la conexión recién hecha tarda un poco en servir)
				switch err := conCerrojo(func() error { return envia(false) }); {
				case err == nil:
					ultimo, enviada = time.Now(), h
				case errors.Is(err, errOcupado):
					ahora = false // lo está mandando otro (el menú): se mira otra vez en un minuto, por si falla
				default:
					log.Print("ERROR: ", err, " (se reintenta en un minuto)")
					ahora = false
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

// abreRegistro apunta en [archivo] (y en la pantalla, si se lanza a mano). El archivo se abre y se cierra en cada línea: así no queda
// nada abierto en la memoria del Kobo (si no, al enchufarlo por USB no podría prestarla). Si pasa de 64 KB, se empieza de nuevo
// guardando el anterior. Con la memoria desmontada (enchufado por USB), no se apunta nada.
func abreRegistro(archivo string) {
	log.SetFlags(0)
	log.SetOutput(io.MultiWriter(os.Stderr, registro(archivo)))
}

type registro string

func (r registro) Write(p []byte) (int, error) {
	archivo := string(r)
	if !montado(archivo) {
		return len(p), nil
	}
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
