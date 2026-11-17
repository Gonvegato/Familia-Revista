package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"
)

// Cuenta es lo que deja Seshat en el Kobo al prepararlo (`.adds/seshat/cuenta.json`): con qué cuenta hablar y el pase que se
// renueva (nunca la contraseña). Si Google da un pase nuevo al renovarlo, se guarda.
type Cuenta struct {
	Uid         string `json:"uid"`
	Refresco    string `json:"refresco"`
	ClaveWeb    string `json:"claveWeb"`
	BaseDatos   string `json:"baseDatos"`
	BaseToken   string `json:"baseToken"`
	Extra       string `json:"extra,omitempty"` // (parámetros de más en cada dirección: el emulador pide «ns=<proyecto>»)
	Zona        string `json:"zona"`
	Dispositivo string `json:"dispositivo"`

	archivo string
	pase    string
	cliente *http.Client
}

func cargaCuenta(archivo string) (*Cuenta, error) {
	b, err := os.ReadFile(archivo)
	if err != nil {
		return nil, fmt.Errorf("el Kobo no está preparado (falta %s: prepáralo desde Seshat)", archivo)
	}
	c := &Cuenta{archivo: archivo, cliente: &http.Client{Timeout: 25 * time.Second}}
	if err := json.Unmarshal(b, c); err != nil {
		return nil, fmt.Errorf("%s está mal: %w", archivo, err)
	}
	if c.Uid == "" || c.Refresco == "" || c.ClaveWeb == "" || c.BaseDatos == "" {
		return nil, fmt.Errorf("%s está incompleto: vuelve a preparar el Kobo desde Seshat", archivo)
	}
	if c.BaseToken == "" {
		c.BaseToken = "https://securetoken.googleapis.com"
	}
	if c.Dispositivo == "" {
		c.Dispositivo = "kobo"
	}
	return c, nil
}

func (c *Cuenta) zona() *time.Location {
	if z, err := time.LoadLocation(c.Zona); err == nil && c.Zona != "" {
		return z
	}
	return time.UTC
}

// entra renueva el pase con el `refresh token` (como hace Seshat).
func (c *Cuenta) entra() error {
	f := url.Values{"grant_type": {"refresh_token"}, "refresh_token": {c.Refresco}}
	r, err := c.cliente.PostForm(strings.TrimRight(c.BaseToken, "/")+"/v1/token?key="+url.QueryEscape(c.ClaveWeb), f)
	if err != nil {
		return fmt.Errorf("sin conexión con la cuenta: %w", err)
	}
	defer r.Body.Close()
	var m map[string]any
	_ = json.NewDecoder(r.Body).Decode(&m)
	if r.StatusCode != 200 {
		return fmt.Errorf("la cuenta no deja entrar (%d): vuelve a preparar el Kobo desde Seshat", r.StatusCode)
	}
	c.pase = texto(m["id_token"])
	if u := texto(m["user_id"]); u != "" && u != c.Uid {
		return fmt.Errorf("el pase es de otra cuenta")
	}
	if nuevo := texto(m["refresh_token"]); nuevo != "" && nuevo != c.Refresco {
		c.Refresco = nuevo
		if b, err := json.MarshalIndent(c, "", "  "); err == nil {
			_ = os.WriteFile(c.archivo, b, 0o600)
		}
	}
	if c.pase == "" {
		return fmt.Errorf("la cuenta no ha dado pase")
	}
	return nil
}

func (c *Cuenta) url(ruta string) string {
	q := "auth=" + url.QueryEscape(c.pase)
	if c.Extra != "" {
		q = c.Extra + "&" + q
	}
	return strings.TrimRight(c.BaseDatos, "/") + ruta + ".json?" + q
}

func (c *Cuenta) lee(ruta string) (Nodo, error) {
	r, err := c.cliente.Get(c.url(ruta))
	if err != nil {
		return nil, err
	}
	defer r.Body.Close()
	b, _ := io.ReadAll(r.Body)
	if r.StatusCode != 200 {
		return nil, fmt.Errorf("leer %s: %d %s", ruta, r.StatusCode, strings.TrimSpace(string(b)))
	}
	var n Nodo
	if err := json.Unmarshal(b, &n); err != nil {
		return nil, nil // («null»: no existe)
	}
	return n, nil
}

func (c *Cuenta) sube(cambios map[string]any) error {
	b, _ := json.Marshal(cambios)
	q, _ := http.NewRequest("PATCH", c.url("/users/"+c.Uid), bytes.NewReader(b))
	q.Header.Set("Content-Type", "application/json")
	r, err := c.cliente.Do(q)
	if err != nil {
		return err
	}
	defer r.Body.Close()
	if r.StatusCode != 200 {
		cuerpo, _ := io.ReadAll(r.Body)
		return fmt.Errorf("la cuenta no ha aceptado los cambios (%d): %s", r.StatusCode, strings.TrimSpace(string(cuerpo)))
	}
	return nil
}
