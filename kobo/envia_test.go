package main

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"
)

// Una cuenta de mentira que hace lo que hace la de verdad con lo que le manda el programa: GET de un nodo, PATCH de varias rutas a la
// vez (todo o nada) con la regla de la cuenta (un campo solo si su hora `m` es más nueva que la guardada). Y puede fallar a propósito:
// perder la respuesta de un PATCH que sí ha aplicado, o cortar una lectura a mitad.
type cuentaFalsa struct {
	mu              sync.Mutex
	arbol           map[string]any
	pierdeRespuesta bool
	cortaLectura    bool
	patches         int
}

func (f *cuentaFalsa) nodo(ruta string) any {
	var x any = f.arbol
	for _, p := range strings.Split(strings.Trim(ruta, "/"), "/") {
		m, _ := x.(map[string]any)
		if m == nil {
			return nil
		}
		x = m[p]
	}
	return x
}

func (f *cuentaFalsa) pon(ruta string, v any) {
	m := f.arbol
	ps := strings.Split(strings.Trim(ruta, "/"), "/")
	for _, p := range ps[:len(ps)-1] {
		h, _ := m[p].(map[string]any)
		if h == nil {
			h = map[string]any{}
			m[p] = h
		}
		m = h
	}
	m[ps[len(ps)-1]] = v
}

func (f *cuentaFalsa) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if r.URL.Path == "/v1/token" {
		fmt.Fprint(w, `{"id_token":"pase","user_id":"u1"}`)
		return
	}
	ruta := strings.TrimSuffix(r.URL.Path, ".json")
	switch r.Method {
	case "GET":
		b, _ := json.Marshal(f.nodo(ruta))
		if f.cortaLectura {
			w.Header().Set("Content-Length", fmt.Sprint(len(b)+100))
			w.Write(b[:len(b)/2])
			return
		}
		w.Write(b)
	case "PATCH":
		var cambios map[string]any
		b, _ := io.ReadAll(r.Body)
		json.Unmarshal(b, &cambios)
		// La regla de la cuenta, para todo lo que se manda junto (si un campo no pasa, no se aplica nada)
		for k := range cambios {
			p := strings.Split(k, "/")
			if len(p) != 4 || p[0] != "familia" || p[3] == "u" || p[3] == "m" {
				continue
			}
			nodo := ruta + "/familia/" + p[1] + "/" + p[2]
			nueva, ok := cambios["familia/"+p[1]+"/"+p[2]+"/m/"+p[3]].(float64)
			if !ok {
				nueva, _ = f.nodo(nodo + "/m/" + p[3]).(float64)
			}
			if vieja, hay := f.nodo(nodo + "/m/" + p[3]).(float64); hay && !(nueva > vieja) || nueva == 0 {
				http.Error(w, "permission denied: "+k, 401)
				return
			}
		}
		for k, v := range cambios {
			if m, ok := v.(map[string]any); ok && m[".sv"] == "timestamp" {
				v = float64(time.Now().UnixMilli())
			}
			f.pon(ruta+"/"+k, v)
		}
		f.patches++
		if f.pierdeRespuesta {
			f.pierdeRespuesta = false
			http.Error(w, "se cortó", 502) // (aplicado, pero el Kobo no se entera)
			return
		}
		w.Write(b)
	}
}

type entrada struct {
	id, titulo, autor string
	seg               int64
	ul                string
}

// koboFalso deja una base de datos del Kobo con [es] y prepara el programa para usarla (con la cuenta [srv]).
func koboFalso(t *testing.T, dir string, srv *httptest.Server) {
	t.Helper()
	os.MkdirAll(filepath.Join(dir, ".kobo"), 0o755)
	os.MkdirAll(filepath.Join(dir, ".adds", "seshat"), 0o755)
	*fBD = filepath.Join(dir, ".kobo", "KoboReader.sqlite")
	dirSeshat = filepath.Join(dir, ".adds", "seshat")
	*fCuenta = filepath.Join(dirSeshat, "cuenta.json")
	*fEnviado = filepath.Join(dirSeshat, "enviado.json")
	c := fmt.Sprintf(`{"uid":"u1","refresco":"r","claveWeb":"k","baseDatos":%q,"baseToken":%q,"zona":"Europe/Madrid","dispositivo":"kobo"}`, srv.URL, srv.URL)
	os.WriteFile(*fCuenta, []byte(c), 0o600)
}

func ponKobo(t *testing.T, es ...entrada) {
	t.Helper()
	os.Remove(*fBD)
	db, err := sql.Open("sqlite", "file:"+*fBD)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	db.Exec(`CREATE TABLE content (ContentID TEXT, ContentType TEXT, Title TEXT, Attribution TEXT, ___PercentRead INTEGER,
		ReadStatus INTEGER, DateLastRead TEXT, TimeSpentReading INTEGER)`)
	for _, e := range es {
		if _, err := db.Exec("INSERT INTO content VALUES (?,?,?,?,?,?,?,?)", e.id, "6", e.titulo, e.autor, 40, 1, e.ul, e.seg); err != nil {
			t.Fatal(err)
		}
	}
	// (la hora del archivo cambia aunque el tamaño no: así el programa ve que hay algo nuevo)
	futuro := time.Now().Add(time.Duration(len(es)) * time.Second)
	os.Chtimes(*fBD, futuro, futuro)
}

func tiempoDe(f *cuentaFalsa, titulo, autor string) map[string]any {
	m, _ := f.nodo("users/u1/familia/tiempo/" + kid(claveDe(titulo, autor)) + "~kobo").(map[string]any)
	return m
}

func nuevaCuenta(t *testing.T) (*cuentaFalsa, *httptest.Server) {
	f := &cuentaFalsa{arbol: map[string]any{}}
	srv := httptest.NewServer(f)
	t.Cleanup(srv.Close)
	koboFalso(t, t.TempDir(), srv)
	return f, srv
}

const dia15 = "2026-11-15T20:00:00Z" // (el 15 en Madrid)

// Se manda, la cuenta lo aplica pero la respuesta se pierde; se lee más y se vuelve a mandar: el tiempo no cuenta dos veces.
func TestEnviaRespuestaPerdida(t *testing.T) {
	f, _ := nuevaCuenta(t)
	rojo := entrada{"file:///mnt/onboard/rojo.epub", "Amanecer rojo", "Pierce Brown", 0, dia15}
	ponKobo(t, rojo)
	if err := envia(false); err != nil {
		t.Fatal(err)
	}
	rojo.seg = 600
	ponKobo(t, rojo)
	f.pierdeRespuesta = true
	if err := envia(false); err == nil {
		t.Fatal("debería dar error (respuesta perdida)")
	}
	rojo.seg = 900
	ponKobo(t, rojo)
	if err := envia(false); err != nil {
		t.Fatal(err)
	}
	ti := tiempoDe(f, rojo.titulo, rojo.autor)
	if ti["total"] != 900000.0 || ti["d2026-11-15"] != 900000.0 {
		t.Fatalf("15 min leídos, en la cuenta: %v", ti)
	}
	// Y mandarlo otra vez sin leer más no cambia nada
	n := f.patches
	ponKobo(t, rojo)
	if err := envia(false); err != nil || f.patches != n {
		t.Fatalf("sin nada nuevo no se manda: %v, %d patches", err, f.patches-n)
	}
}

// Se borra la muestra y queda el comprado: no es «el Kobo ha vuelto a cero» (ni tiempo de más ni un día falso).
func TestEnviaBorraDuplicado(t *testing.T) {
	f, _ := nuevaCuenta(t)
	muestra := entrada{"muestra-id", "Amanecer rojo", "Pierce Brown", 600, dia15}
	comprado := entrada{"comprado-id", "Amanecer rojo", "Pierce Brown", 3000, dia15}
	ponKobo(t, muestra, comprado)
	if err := envia(false); err != nil {
		t.Fatal(err)
	}
	if ti := tiempoDe(f, "Amanecer rojo", "Pierce Brown"); ti["total"] != 3600000.0 {
		t.Fatalf("%v", ti)
	}
	ponKobo(t, comprado)
	if err := envia(false); err != nil {
		t.Fatal(err)
	}
	comprado.seg = 3060
	ponKobo(t, comprado)
	if err := envia(false); err != nil {
		t.Fatal(err)
	}
	ti := tiempoDe(f, "Amanecer rojo", "Pierce Brown")
	if ti["total"] != 3660000.0 || ti["d2026-11-15"] != 60000.0 {
		t.Fatalf("1 h leída + 1 min: %v", ti)
	}
}

// Dos libros del Kobo que son el mismo de la cuenta («Pierce Brown» y «Brown, Pierce»): si solo cambia uno, su tiempo se suma igual.
func TestEnviaGrupoParcial(t *testing.T) {
	f, _ := nuevaCuenta(t)
	ka := kid(claveDe("Amanecer rojo", "Pierce Brown"))
	f.pon("users/u1/familia/libros/"+ka, map[string]any{"t": "Amanecer rojo", "a": "Pierce Brown", "m": map[string]any{"t": 1.0, "a": 1.0}})
	a := entrada{"a-id", "Amanecer rojo", "Pierce Brown", 1000, dia15}
	b := entrada{"b-id", "Amanecer rojo", "Brown, Pierce", 2000, dia15}
	ponKobo(t, a, b)
	if err := envia(false); err != nil {
		t.Fatal(err)
	}
	if ti := tiempoDe(f, "Amanecer rojo", "Pierce Brown"); ti["total"] != 3000000.0 {
		t.Fatalf("%v", ti)
	}
	a.seg = 1600
	ponKobo(t, a, b)
	if err := envia(false); err != nil {
		t.Fatal(err)
	}
	ti := tiempoDe(f, "Amanecer rojo", "Pierce Brown")
	if ti["total"] != 3600000.0 || ti["d2026-11-15"] != 600000.0 {
		t.Fatalf("10 min más: %v", ti)
	}
	if f.nodo("users/u1/familia/libros/"+kid(claveDe("Amanecer rojo", "Brown, Pierce"))) != nil {
		t.Fatal("no tiene que crear otro libro")
	}
}

// Una lectura de la cuenta cortada a mitad no se toma por «no hay nada»: no se manda nada.
func TestEnviaLecturaCortada(t *testing.T) {
	f, _ := nuevaCuenta(t)
	f.pon("users/u1/familia/tiempo/"+kid(claveDe("Amanecer rojo", "Pierce Brown"))+"~kobo", map[string]any{"total": 3600000.0, "d2026-11-14": 3600000.0, "m": map[string]any{"total": 5.0, "d2026-11-14": 5.0}})
	rojo := entrada{"file:///mnt/onboard/rojo.epub", "Amanecer rojo", "Pierce Brown", 3600, dia15}
	ponKobo(t, rojo)
	f.cortaLectura = true
	if err := envia(false); err == nil || f.patches != 0 {
		t.Fatalf("debería fallar sin mandar nada: %v, %d", err, f.patches)
	}
}

// Lo leído antes de que el programa apuntara por días (o con el cable): al día de la última lectura, una sola vez.
func TestEnviaLoDeAntes(t *testing.T) {
	f, _ := nuevaCuenta(t)
	k := kid(claveDe("Amanecer rojo", "Pierce Brown"))
	f.pon("users/u1/familia/tiempo/"+k+"~kobo", map[string]any{"total": 1800000.0, "d2026-11-10": 1800000.0, "m": map[string]any{"total": 5.0, "d2026-11-10": 5.0}})
	rojo := entrada{"file:///mnt/onboard/rojo.epub", "Amanecer rojo", "Pierce Brown", 3600, dia15}
	ponKobo(t, rojo)
	if err := envia(false); err != nil {
		t.Fatal(err)
	}
	rojo.seg = 3900
	ponKobo(t, rojo)
	if err := envia(false); err != nil {
		t.Fatal(err)
	}
	ti := tiempoDe(f, rojo.titulo, rojo.autor)
	// 30 min de antes (el 10) + 30 min sin día conocido (al 15, el de la última lectura) + 5 min el 15 (ya apuntados por el programa)
	if ti["total"] != 3900000.0 || ti["d2026-11-10"] != 1800000.0 || ti["d2026-11-15"] != 2100000.0 || ti["k2026-11-15"] != 300000.0 {
		t.Fatalf("%v", ti)
	}
}
