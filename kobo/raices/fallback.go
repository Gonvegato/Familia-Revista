// Las raíces de confianza de Mozilla (NSS), copiadas de golang.org/x/crypto/x509roots/fallback (licencia BSD, en LICENSE): el Kobo
// puede no traer las suyas, y sin ellas no se podría hablar con la cuenta por HTTPS. Se ponen como «de reserva»: si el sistema
// tiene las suyas, se usan esas.
package raices

import "crypto/x509"

func init() {
	p := x509.NewCertPool()
	for c := range Roots() {
		cert, err := x509.ParseCertificate(c.Certificate)
		if err != nil {
			continue
		}
		if c.Constraint == nil {
			p.AddCert(cert)
		} else {
			p.AddCertWithConstraint(cert, c.Constraint)
		}
	}
	x509.SetFallbackRoots(p)
}
