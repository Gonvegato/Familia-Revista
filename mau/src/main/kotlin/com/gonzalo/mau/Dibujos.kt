// Generado por herramientas/mau/genera_dibujos.py desde docs/mau/svg: no lo edites a mano.
package com.gonzalo.mau

@Suppress("LargeClass")
internal object Dibujos {
    /** El dibujo de cada postura (se arma una vez y se guarda). */
    fun de(p: Pose): Dibujo = cache.getOrPut(p) { arma(p) }
    private val cache = java.util.concurrent.ConcurrentHashMap<Pose, Dibujo>()
    private fun arma(p: Pose): Dibujo = when (p) {
        Pose.SENTADO -> sentado()
        Pose.LEYENDO -> leyendo()
        Pose.ESCRIBIENDO -> escribiendo()
        Pose.VIENDO -> viendo()
        Pose.CELEBRANDO -> celebrando()
        Pose.TAPANDOSE -> tapandose()
        Pose.DURMIENDO -> durmiendo()
        Pose.JUGANDO -> jugando()
        Pose.RONRONEANDO -> ronroneando()
        Pose.BOSTEZANDO -> bostezando()
        Pose.ESCONDIDO -> escondido()
    }

    private fun sentado() = Dibujo(200f, 220f, Grupo(null, 1f, listOf(
            Grupo("contorno", 1f, listOf(
                Trazo("M79 106C74 124 70 140 69 156C56 165 52 186 54 200C55 209 59 214 66 214L134 214C141 214 145 209 146 200C148 186 144 165 131 156C130 140 126 124 121 106Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                Grupo("contorno-cola", 1f, listOf(
                    Trazo("M138 206C168 206 178 182 172 158C168 142 168 128 181 123", 0x00000000, false, 0xFF4A4444, false, 20f, 1, 1, 1f)
                )),
                Grupo("contorno-cabeza", 1f, listOf(
                    Trazo("M100 47C128 47 145 57 145 79C145 101 128 115 100 115C72 115 55 101 55 79C55 57 72 47 100 47Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                    Grupo("contorno-oreja-izq", 1f, listOf(
                        Trazo("M59 72L56 17L95 50Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    )),
                    Grupo("contorno-oreja-der", 1f, listOf(
                        Trazo("M141 72L144 17L105 50Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    ))
                ))
            )),
            Grupo("cuerpo", 1f, listOf(
                Grupo("cola", 1f, listOf(
                    Trazo("M138 206C168 206 178 182 172 158C168 142 168 128 181 123", 0x00000000, false, 0xFF141414, false, 13f, 1, 1, 1f)
                )),
                Trazo("M79 106C74 124 70 140 69 156C56 165 52 186 54 200C55 209 59 214 66 214L134 214C141 214 145 209 146 200C148 186 144 165 131 156C130 140 126 124 121 106Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M69 159C59 167 55 185 56 199C57 207 60 211.5 66 211.5L76 211.5C74 194 74 175 69 159Z", 0xFF2A2626, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M131 159C141 167 145 185 144 199C143 207 140 211.5 134 211.5L124 211.5C126 194 126 175 131 159Z", 0xFF2A2626, false, 0x00000000, false, 0f, 0, 0, 1f),
                Grupo("pata-izq", 1f, listOf(
                    Trazo("M84 150C83.5 170 83 192 83 206C83 211 86 214 90 214L98 214C99.5 214 100 213 100 211L100 150Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M100 166L100 206", 0x00000000, false, 0xFF2A2626, false, 1.6f, 1, 1, 1f)
                )),
                Grupo("pata-der", 1f, listOf(
                    Trazo("M116 150C116.5 170 117 192 117 206C117 211 114 214 110 214L102 214C100.5 214 100 213 100 211L100 150Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Grupo("collar", 1f, listOf(
                    Trazo("M76 109Q100 123 124 109L124.5 114Q100 128 75.5 114Z", 0x00000000, true, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M95 127A5 5 0 1 0 105 127A5 5 0 1 0 95 127Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Grupo("cabeza", 1f, listOf(
                    Grupo("oreja-izq", 1f, listOf(
                        Trazo("M59 72L56 17L95 50Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M64 60L62 30L86 50Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Grupo("oreja-der", 1f, listOf(
                        Trazo("M141 72L144 17L105 50Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M136 60L138 30L114 50Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Trazo("M100 47C128 47 145 57 145 79C145 101 128 115 100 115C72 115 55 101 55 79C55 57 72 47 100 47Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Grupo("ojos-abiertos", 1f, listOf(
                        Trazo("M66.5 80A12.5 12.5 0 1 0 91.5 80A12.5 12.5 0 1 0 66.5 80Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M74.4 82A4.6 7.5 0 1 0 83.6 82A4.6 7.5 0 1 0 74.4 82Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M108.5 80A12.5 12.5 0 1 0 133.5 80A12.5 12.5 0 1 0 108.5 80Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M116.4 82A4.6 7.5 0 1 0 125.6 82A4.6 7.5 0 1 0 116.4 82Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M64 63L94 63L94 74.5L64 72.5Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M136 63L106 63L106 74.5L136 72.5Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M80.6 78A2.4 2.4 0 1 0 85.4 78A2.4 2.4 0 1 0 80.6 78Z", 0xFFFFFFFF, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M122.6 78A2.4 2.4 0 1 0 127.4 78A2.4 2.4 0 1 0 122.6 78Z", 0xFFFFFFFF, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Grupo("ojos-cerrados", 0f, listOf(
                        Trazo("M67.5 83Q79 90 90.5 83M132.5 83Q121 90 109.5 83", 0x00000000, false, 0xFFE8C34A, false, 3f, 1, 1, 1f)
                    )),
                    Trazo("M95 95L105 95L100 100.5Z", 0xFF8A5252, false, 0xFF8A5252, false, 1.5f, 0, 1, 1f),
                    Trazo("M77 101L47 98M123 101L153 98M77 105L49 110M123 105L151 110", 0x00000000, false, 0xFF6A6A6A, false, 1.3f, 1, 1, 1f)
                ))
            ))
        )))

    private fun leyendo() = Dibujo(200f, 220f, Grupo(null, 1f, listOf(
            Grupo("contorno", 1f, listOf(
                Trazo("M79 106C74 124 70 140 69 156C56 165 52 186 54 200C55 209 59 214 66 214L134 214C141 214 145 209 146 200C148 186 144 165 131 156C130 140 126 124 121 106Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                Grupo("contorno-cola", 1f, listOf(
                    Trazo("M138 206C168 206 178 182 172 158C168 142 168 128 181 123", 0x00000000, false, 0xFF4A4444, false, 20f, 1, 1, 1f)
                )),
                Grupo("contorno-cabeza", 1f, listOf(
                    Trazo("M100 52C128 52 145 62 145 84C145 106 128 120 100 120C72 120 55 106 55 84C55 62 72 52 100 52Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                    Grupo("contorno-oreja-izq", 1f, listOf(
                        Trazo("M59 77L56 22L95 55Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    )),
                    Grupo("contorno-oreja-der", 1f, listOf(
                        Trazo("M141 77L144 22L105 55Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    ))
                ))
            )),
            Grupo("cuerpo", 1f, listOf(
                Grupo("cola", 1f, listOf(
                    Trazo("M138 206C168 206 178 182 172 158C168 142 168 128 181 123", 0x00000000, false, 0xFF141414, false, 13f, 1, 1, 1f)
                )),
                Trazo("M79 106C74 124 70 140 69 156C56 165 52 186 54 200C55 209 59 214 66 214L134 214C141 214 145 209 146 200C148 186 144 165 131 156C130 140 126 124 121 106Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M69 159C59 167 55 185 56 199C57 207 60 211.5 66 211.5L76 211.5C74 194 74 175 69 159Z", 0xFF2A2626, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M131 159C141 167 145 185 144 199C143 207 140 211.5 134 211.5L124 211.5C126 194 126 175 131 159Z", 0xFF2A2626, false, 0x00000000, false, 0f, 0, 0, 1f),
                Grupo("collar", 1f, listOf(
                    Trazo("M76 109Q100 123 124 109L124.5 114Q100 128 75.5 114Z", 0x00000000, true, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M95 127A5 5 0 1 0 105 127A5 5 0 1 0 95 127Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Grupo("libro", 1f, listOf(
                    Trazo("M48 190Q74 181 100 189Q126 181 152 190L154 211.5Q127 204 100 213.5Q73 204 46 211.5Z", 0xFF8A5252, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M52 188Q76 180 100 187L100 208.5Q76 200 50 205.5Z", 0xFFF8F0D4, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M148 188Q124 180 100 187L100 208.5Q124 200 150 205.5Z", 0xFFF8F0D4, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M61 191Q76 186 91 190M60 196.5Q76 191 91 195.5M139 191Q124 186 109 190M140 196.5Q124 191 109 195.5", 0x00000000, false, 0xFF6A6A6A, false, 1.3f, 1, 1, 1f),
                    Trazo("M100 187.5L100 208.5", 0x00000000, false, 0xFF2A2626, false, 1.3f, 1, 1, 1f)
                )),
                Grupo("pata-izq", 1f, listOf(
                    Trazo("M81 146C77 160 71 174 66 186C63 193 66 198 72 198L80 198C83.5 198 85.5 196 86.5 191C89 177 91 163 95 148Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Grupo("pata-der", 1f, listOf(
                    Trazo("M119 146C123 160 129 174 134 186C137 193 134 198 128 198L120 198C116.5 198 114.5 196 113.5 191C111 177 109 163 105 148Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Grupo("cabeza", 1f, listOf(
                    Grupo("oreja-izq", 1f, listOf(
                        Trazo("M59 77L56 22L95 55Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M64 65L62 35L86 55Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Grupo("oreja-der", 1f, listOf(
                        Trazo("M141 77L144 22L105 55Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M136 65L138 35L114 55Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Trazo("M100 52C128 52 145 62 145 84C145 106 128 120 100 120C72 120 55 106 55 84C55 62 72 52 100 52Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Grupo("ojos-abiertos", 1f, listOf(
                        Trazo("M66.5 85A12.5 12.5 0 1 0 91.5 85A12.5 12.5 0 1 0 66.5 85Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M74.4 90A4.6 7.5 0 1 0 83.6 90A4.6 7.5 0 1 0 74.4 90Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M108.5 85A12.5 12.5 0 1 0 133.5 85A12.5 12.5 0 1 0 108.5 85Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M116.4 90A4.6 7.5 0 1 0 125.6 90A4.6 7.5 0 1 0 116.4 90Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M64 68L94 68L94 79.5L64 77.5Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M136 68L106 68L106 79.5L136 77.5Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M80.6 84.2A2.4 2.4 0 1 0 85.4 84.2A2.4 2.4 0 1 0 80.6 84.2Z", 0xFFFFFFFF, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M122.6 84.2A2.4 2.4 0 1 0 127.4 84.2A2.4 2.4 0 1 0 122.6 84.2Z", 0xFFFFFFFF, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Grupo("ojos-cerrados", 0f, listOf(
                        Trazo("M67.5 88Q79 95 90.5 88M132.5 88Q121 95 109.5 88", 0x00000000, false, 0xFFE8C34A, false, 3f, 1, 1, 1f)
                    )),
                    Trazo("M95 100L105 100L100 105.5Z", 0xFF8A5252, false, 0xFF8A5252, false, 1.5f, 0, 1, 1f),
                    Trazo("M77 106L47 103M123 106L153 103M77 110L49 115M123 110L151 115", 0x00000000, false, 0xFF6A6A6A, false, 1.3f, 1, 1, 1f),
                    Grupo("gafas", 1f, listOf(
                        Trazo("M63.5 85A15.5 15.5 0 1 0 94.5 85A15.5 15.5 0 1 0 63.5 85Z", 0x00000000, false, 0xFFF8F0D4, false, 2.6f, 0, 0, 1f),
                        Trazo("M105.5 85A15.5 15.5 0 1 0 136.5 85A15.5 15.5 0 1 0 105.5 85Z", 0x00000000, false, 0xFFF8F0D4, false, 2.6f, 0, 0, 1f),
                        Trazo("M94 84Q100 79.5 106 84M64 81.5L57 78.5M136 81.5L143 78.5", 0x00000000, false, 0xFFF8F0D4, false, 2.6f, 1, 1, 1f)
                    ))
                ))
            ))
        )))

    private fun escribiendo() = Dibujo(200f, 220f, Grupo(null, 1f, listOf(
            Grupo("contorno", 1f, listOf(
                Trazo("M79 106C74 124 70 140 69 156C56 165 52 186 54 200C55 209 59 214 66 214L134 214C141 214 145 209 146 200C148 186 144 165 131 156C130 140 126 124 121 106Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                Grupo("contorno-cola", 1f, listOf(
                    Trazo("M138 206C168 206 178 182 172 158C168 142 168 128 181 123", 0x00000000, false, 0xFF4A4444, false, 20f, 1, 1, 1f)
                )),
                Grupo("contorno-pata-der", 1f, listOf(
                    Trazo("M110 148C121 158 127 170 125 186", 0x00000000, false, 0xFF4A4444, false, 21f, 1, 1, 1f)
                )),
                Grupo("contorno-cabeza", 1f, listOf(
                    Trazo("M103.6 51.2C131.4 54.1 147.3 65.8 145 87.7C142.7 109.6 124.3 121.7 96.4 118.8C68.6 115.9 53.2 100.2 55.5 78.3C57.8 56.4 75.7 48.3 103.6 51.2Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                    Grupo("contorno-oreja-izq", 1f, listOf(
                        Trazo("M60.2 71.8L62.9 16.8L98.3 53.6Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    )),
                    Grupo("contorno-oreja-der", 1f, listOf(
                        Trazo("M141.7 80.3L150.4 25.9L108.2 54.7Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    ))
                ))
            )),
            Grupo("cuerpo", 1f, listOf(
                Grupo("cola", 1f, listOf(
                    Trazo("M138 206C168 206 178 182 172 158C168 142 168 128 181 123", 0x00000000, false, 0xFF141414, false, 13f, 1, 1, 1f)
                )),
                Trazo("M79 106C74 124 70 140 69 156C56 165 52 186 54 200C55 209 59 214 66 214L134 214C141 214 145 209 146 200C148 186 144 165 131 156C130 140 126 124 121 106Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M69 159C59 167 55 185 56 199C57 207 60 211.5 66 211.5L76 211.5C74 194 74 175 69 159Z", 0xFF2A2626, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M131 159C141 167 145 185 144 199C143 207 140 211.5 134 211.5L124 211.5C126 194 126 175 131 159Z", 0xFF2A2626, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M52 199L126 194L132 213.5L48 214Z", 0xFFF8F0D4, false, 0xFF141414, false, 1.5f, 0, 1, 1f),
                Trazo("M60 204L100 201.5M58 208.5L112 205.5", 0x00000000, false, 0xFF6A6A6A, false, 1.3f, 1, 1, 1f),
                Grupo("pata-izq", 1f, listOf(
                    Trazo("M84 150C83.5 170 83 192 83 206C83 211 86 214 90 214L98 214C99.5 214 100 213 100 211L100 150Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M100 166L100 206", 0x00000000, false, 0xFF2A2626, false, 1.6f, 1, 1, 1f)
                )),
                Grupo("collar", 1f, listOf(
                    Trazo("M76 109Q100 123 124 109L124.5 114Q100 128 75.5 114Z", 0x00000000, true, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M95 127A5 5 0 1 0 105 127A5 5 0 1 0 95 127Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Grupo("pluma", 1f, listOf(
                    Trazo("M125.5 185.4C124.4 167.1 133.1 144.6 153.5 130.1C151.9 151.6 141.4 173.1 125.5 185.4Z", 0xFFF8F0D4, false, 0xFF141414, false, 1.4f, 0, 1, 1f),
                    Trazo("M116.3 204.3L150.8 133.2", 0x00000000, false, 0xFF6A6A6A, false, 1.3f, 1, 1, 1f),
                    Trazo("M118.7 194.3L115 207L122.7 196.3Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Grupo("pata-der", 1f, listOf(
                    Trazo("M120.5 166C125 173 126.5 180 125 186", 0x00000000, false, 0xFF2A2626, false, 17f, 1, 1, 1f),
                    Trazo("M110 148C121 158 127 170 125 186", 0x00000000, false, 0xFF141414, false, 14f, 1, 1, 1f)
                )),
                Grupo("cabeza", 1f, listOf(
                    Grupo("oreja-izq", 1f, listOf(
                        Trazo("M60.2 71.8L62.9 16.8L98.3 53.6Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M66.4 60.4L67.5 30.3L89.3 52.7Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Grupo("oreja-der", 1f, listOf(
                        Trazo("M141.7 80.3L150.4 25.9L108.2 54.7Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M138 67.9L143.1 38.3L117.2 55.6Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Trazo("M103.6 51.2C131.4 54.1 147.3 65.8 145 87.7C142.7 109.6 124.3 121.7 96.4 118.8C68.6 115.9 53.2 100.2 55.5 78.3C57.8 56.4 75.7 48.3 103.6 51.2Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Grupo("ojos-abiertos", 1f, listOf(
                        Trazo("M66.7 81.8A12.5 12.5 0 1 0 91.7 81.8A12.5 12.5 0 1 0 66.7 81.8Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M76.6 87A4.6 7.5 0 1 0 85.8 87A4.6 7.5 0 1 0 76.6 87Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M108.5 86.2A12.5 12.5 0 1 0 133.5 86.2A12.5 12.5 0 1 0 108.5 86.2Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M118.4 91.4A4.6 7.5 0 1 0 127.6 91.4A4.6 7.5 0 1 0 118.4 91.4Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M66.1 63.3L95.9 66.5L94.7 77.9L65.1 72.8Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M137.7 70.9L107.8 67.7L106.6 79.2L136.7 80.3Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M81.9 81.5A2.4 2.4 0 1 0 86.7 81.5A2.4 2.4 0 1 0 81.9 81.5Z", 0xFFFFFFFF, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M123.6 85.9A2.4 2.4 0 1 0 128.4 85.9A2.4 2.4 0 1 0 123.6 85.9Z", 0xFFFFFFFF, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Grupo("ojos-cerrados", 0f, listOf(
                        Trazo("M67.5 83.6Q78.2 91.8 90.3 86M132.1 90.4Q119.9 96.1 109.2 88", 0x00000000, false, 0xFFE8C34A, false, 3f, 1, 1, 1f)
                    )),
                    Trazo("M93.6 98.4L103.5 99.4L98 104.4Z", 0xFF8A5252, false, 0xFF8A5252, false, 1.5f, 0, 1, 1f),
                    Trazo("M75 102.5L45.5 96.4M120.8 107.3L150.9 107.4M74.6 106.5L46.2 108.5M120.4 111.3L147.7 119.2", 0x00000000, false, 0xFF6A6A6A, false, 1.3f, 1, 1, 1f)
                ))
            ))
        )))

    private fun viendo() = Dibujo(200f, 220f, Grupo(null, 1f, listOf(
            Trazo("M22 10H178V104H22Z", 0xFFF8F0D4, false, 0xFF141414, false, 4f, 0, 0, 1f),
            Grupo("contorno", 1f, listOf(
                Trazo("M79 106C74 124 70 140 69 156C56 165 52 186 54 200C55 209 59 214 66 214L134 214C141 214 145 209 146 200C148 186 144 165 131 156C130 140 126 124 121 106Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                Grupo("contorno-cabeza", 1f, listOf(
                    Trazo("M100 47C128 47 145 57 145 79C145 101 128 115 100 115C72 115 55 101 55 79C55 57 72 47 100 47Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                    Grupo("contorno-oreja-izq", 1f, listOf(
                        Trazo("M59 72L56 17L95 50Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    )),
                    Grupo("contorno-oreja-der", 1f, listOf(
                        Trazo("M141 72L144 17L105 50Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    ))
                )),
                Grupo("contorno-cola", 1f, listOf(
                    Trazo("M104 207C84 214 54 212 46 200C40 191 46 182 56 186", 0x00000000, false, 0xFF4A4444, false, 20f, 1, 1, 1f)
                ))
            )),
            Grupo("cuerpo", 1f, listOf(
                Trazo("M79 106C74 124 70 140 69 156C56 165 52 186 54 200C55 209 59 214 66 214L134 214C141 214 145 209 146 200C148 186 144 165 131 156C130 140 126 124 121 106Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M69 159C59 167 55 185 56 199C57 207 60 211.5 66 211.5L76 211.5C74 194 74 175 69 159Z", 0xFF2A2626, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M131 159C141 167 145 185 144 199C143 207 140 211.5 134 211.5L124 211.5C126 194 126 175 131 159Z", 0xFF2A2626, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M100 132C100 156 100 176 100 196", 0x00000000, false, 0xFF2A2626, false, 1.6f, 1, 1, 1f),
                Grupo("collar", 1f, listOf(
                    Trazo("M76 112Q100 122 124 112L124.5 117Q100 127 75.5 117Z", 0x00000000, true, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Grupo("cabeza", 1f, listOf(
                    Grupo("oreja-izq", 1f, listOf(
                        Trazo("M59 72L56 17L95 50Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M64 60L62 30L86 50Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Grupo("oreja-der", 1f, listOf(
                        Trazo("M141 72L144 17L105 50Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M136 60L138 30L114 50Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Trazo("M100 47C128 47 145 57 145 79C145 101 128 115 100 115C72 115 55 101 55 79C55 57 72 47 100 47Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M58 99L42 96M58 103.5L44 107M142 99L158 96M142 103.5L156 107", 0x00000000, false, 0xFF6A6A6A, false, 1.3f, 1, 1, 1f)
                )),
                Grupo("cola", 1f, listOf(
                    Trazo("M104 207C84 214 54 212 46 200C40 191 46 182 56 186", 0x00000000, false, 0xFF2A2626, false, 16f, 1, 1, 1f),
                    Trazo("M104 207C84 214 54 212 46 200C40 191 46 182 56 186", 0x00000000, false, 0xFF141414, false, 13f, 1, 1, 1f)
                ))
            )),
            Grupo("palomitas", 1f, listOf(
                Trazo("M145 175L177 175L172 214L150 214Z", 0xFFF8F0D4, false, 0xFF141414, false, 1.5f, 0, 1, 1f),
                Trazo("M153 175L159 175L158.3 214L154.4 214Z", 0xFF8A5252, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M165 175L171 175L168.3 214L164.4 214Z", 0xFF8A5252, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M144 176C140 168 147 161 153 165C155 157 166 156 168 163C174 159 182 167 178 176Z", 0xFFF8F0D4, false, 0xFF141414, false, 1.5f, 0, 1, 1f),
                Trazo("M150.2 170A1.8 1.8 0 1 0 153.8 170A1.8 1.8 0 1 0 150.2 170Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M161.2 166A1.8 1.8 0 1 0 164.8 166A1.8 1.8 0 1 0 161.2 166Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M169.2 171A1.8 1.8 0 1 0 172.8 171A1.8 1.8 0 1 0 169.2 171Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f)
            ))
        )))

    private fun celebrando() = Dibujo(200f, 220f, Grupo(null, 1f, listOf(
            Grupo("contorno", 1f, listOf(
                Trazo("M79 106C74 124 70 140 69 156C56 165 52 186 54 200C55 209 59 214 66 214L134 214C141 214 145 209 146 200C148 186 144 165 131 156C130 140 126 124 121 106Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                Grupo("contorno-cola", 1f, listOf(
                    Trazo("M138 206C168 206 178 182 172 158C168 142 168 128 181 123", 0x00000000, false, 0xFF4A4444, false, 20f, 1, 1, 1f)
                )),
                Grupo("contorno-cabeza", 1f, listOf(
                    Trazo("M97 44.1C124.9 41.7 142.7 50.2 144.7 72.1C146.6 94 130.9 109.4 103 111.9C75.1 114.3 56.9 101.8 55 79.9C53.1 58 69.1 46.6 97 44.1Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                    Grupo("contorno-oreja-izq", 1f, listOf(
                        Trazo("M58.4 72.6L50.6 18.1L92.3 47.6Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    )),
                    Grupo("contorno-oreja-der", 1f, listOf(
                        Trazo("M140.1 65.5L138.3 10.4L102.3 46.7Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    ))
                ))
            )),
            Grupo("cuerpo", 1f, listOf(
                Grupo("cola", 1f, listOf(
                    Trazo("M138 206C168 206 178 182 172 158C168 142 168 128 181 123", 0x00000000, false, 0xFF141414, false, 13f, 1, 1, 1f)
                )),
                Trazo("M79 106C74 124 70 140 69 156C56 165 52 186 54 200C55 209 59 214 66 214L134 214C141 214 145 209 146 200C148 186 144 165 131 156C130 140 126 124 121 106Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M69 159C59 167 55 185 56 199C57 207 60 211.5 66 211.5L76 211.5C74 194 74 175 69 159Z", 0xFF2A2626, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M131 159C141 167 145 185 144 199C143 207 140 211.5 134 211.5L124 211.5C126 194 126 175 131 159Z", 0xFF2A2626, false, 0x00000000, false, 0f, 0, 0, 1f),
                Grupo("pata-izq", 1f, listOf(
                    Trazo("M84 150C83.5 170 83 192 83 206C83 211 86 214 90 214L98 214C99.5 214 100 213 100 211L100 150Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M100 166L100 206", 0x00000000, false, 0xFF2A2626, false, 1.6f, 1, 1, 1f)
                )),
                Grupo("pata-der", 1f, listOf(
                    Trazo("M116 150C116.5 170 117 192 117 206C117 211 114 214 110 214L102 214C100.5 214 100 213 100 211L100 150Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Grupo("bufanda", 1f, listOf(
                    Trazo("M104 124L118 119L125 158L110 162Z", 0x00000000, true, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M107.7 147.6L122.3 143.2L123 147.1L108.3 151.4Z", 0xFFFFFFFF, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M108.8 154.4L123.6 150.2L124.3 154.1L109.4 158.2Z", 0xFFFFFFFF, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M72 106Q100 124 128 106L130 116Q100 134 70 116Z", 0x00000000, true, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Grupo("cabeza", 1f, listOf(
                    Grupo("oreja-izq", 1f, listOf(
                        Trazo("M58.4 72.6L50.6 18.1L92.3 47.6Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M62.3 60.2L57.7 30.5L83.4 48.3Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Grupo("oreja-der", 1f, listOf(
                        Trazo("M140.1 65.5L138.3 10.4L102.3 46.7Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M134 53.9L133.4 23.9L111.2 45.9Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Trazo("M97 44.1C124.9 41.7 142.7 50.2 144.7 72.1C146.6 94 130.9 109.4 103 111.9C75.1 114.3 56.9 101.8 55 79.9C53.1 58 69.1 46.6 97 44.1Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Grupo("ojos-abiertos", 1f, listOf(
                        Trazo("M66.5 78.8A12.5 12.5 0 1 0 91.5 78.8A12.5 12.5 0 1 0 66.5 78.8Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M74.6 80.8A4.6 7.5 0 1 0 83.8 80.8A4.6 7.5 0 1 0 74.6 80.8Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M108.3 75.2A12.5 12.5 0 1 0 133.3 75.2A12.5 12.5 0 1 0 108.3 75.2Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M116.4 77.2A4.6 7.5 0 1 0 125.6 77.2A4.6 7.5 0 1 0 116.4 77.2Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M62.6 63.2L92.5 60.6L93.5 72L63.4 72.7Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M134.3 56.9L104.4 59.5L105.4 71L135.1 66.4Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M80.4 76.5A2.4 2.4 0 1 0 85.2 76.5A2.4 2.4 0 1 0 80.4 76.5Z", 0xFFFFFFFF, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M122.2 72.8A2.4 2.4 0 1 0 127 72.8A2.4 2.4 0 1 0 122.2 72.8Z", 0xFFFFFFFF, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Grupo("ojos-cerrados", 0f, listOf(
                        Trazo("M67.8 82.8Q79.9 88.8 90.7 80.8M132.6 77.2Q121.7 85.1 109.6 79.2", 0x00000000, false, 0xFFE8C34A, false, 3f, 1, 1, 1f)
                    )),
                    Trazo("M96.2 92.4L106.2 91.5L101.7 97.4Z", 0xFF8A5252, false, 0xFF8A5252, false, 1.5f, 0, 1, 1f),
                    Trazo("M93.8 99.1Q101.6 95.9 109.8 97.7Q109.5 111.8 103.1 113.9Q96.5 112.9 93.8 99.1Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M98.2 109.3Q102.5 106.4 107.2 108.5Q103.1 112.9 98.2 109.3Z", 0xFF8A5252, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M78.8 99.9L48.7 99.6M124.7 95.9L154.3 90.3M79.2 103.9L51.7 111.3M125 99.9L153.3 102.4", 0x00000000, false, 0xFF6A6A6A, false, 1.3f, 1, 1, 1f),
                    Trazo("M159.1 87.9Q165.6 93.3 160.1 99.8M164.5 81.4Q175.5 92.5 166.6 105.3", 0x00000000, false, 0xFF6A6A6A, false, 1.6f, 1, 1, 1f)
                ))
            ))
        )))

    private fun tapandose() = Dibujo(200f, 220f, Grupo(null, 1f, listOf(
            Grupo("contorno", 1f, listOf(
                Trazo("M79 106C74 124 70 140 69 156C56 165 52 186 54 200C55 209 59 214 66 214L134 214C141 214 145 209 146 200C148 186 144 165 131 156C130 140 126 124 121 106Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                Grupo("contorno-cabeza", 1f, listOf(
                    Trazo("M100 49C128 49 145 59 145 81C145 103 128 117 100 117C72 117 55 103 55 81C55 59 72 49 100 49Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                    Grupo("contorno-oreja-izq", 1f, listOf(
                        Trazo("M71 83.2L32 44.4L83 42.8Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    )),
                    Grupo("contorno-oreja-der", 1f, listOf(
                        Trazo("M129 83.2L168 44.4L117 42.8Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    ))
                )),
                Grupo("contorno-cola", 1f, listOf(
                    Trazo("M146 196C176 190 184 150 168 116C160 98 152 88 138 84C116 80 84 80 64 86C54 90 51 98 56 105", 0x00000000, false, 0xFF4A4444, false, 20f, 1, 1, 1f)
                ))
            )),
            Grupo("cuerpo", 1f, listOf(
                Trazo("M79 106C74 124 70 140 69 156C56 165 52 186 54 200C55 209 59 214 66 214L134 214C141 214 145 209 146 200C148 186 144 165 131 156C130 140 126 124 121 106Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M69 159C59 167 55 185 56 199C57 207 60 211.5 66 211.5L76 211.5C74 194 74 175 69 159Z", 0xFF2A2626, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M131 159C141 167 145 185 144 199C143 207 140 211.5 134 211.5L124 211.5C126 194 126 175 131 159Z", 0xFF2A2626, false, 0x00000000, false, 0f, 0, 0, 1f),
                Grupo("pata-izq", 1f, listOf(
                    Trazo("M84 150C83.5 170 83 192 83 206C83 211 86 214 90 214L98 214C99.5 214 100 213 100 211L100 150Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M100 166L100 206", 0x00000000, false, 0xFF2A2626, false, 1.6f, 1, 1, 1f)
                )),
                Grupo("pata-der", 1f, listOf(
                    Trazo("M116 150C116.5 170 117 192 117 206C117 211 114 214 110 214L102 214C100.5 214 100 213 100 211L100 150Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Grupo("collar", 1f, listOf(
                    Trazo("M76 109Q100 123 124 109L124.5 114Q100 128 75.5 114Z", 0x00000000, true, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M95 127A5 5 0 1 0 105 127A5 5 0 1 0 95 127Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Grupo("cabeza", 1f, listOf(
                    Grupo("oreja-izq", 1f, listOf(
                        Trazo("M71 83.2L32 44.4L83 42.8Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M66.7 71L45.1 50L76.3 48.8Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Grupo("oreja-der", 1f, listOf(
                        Trazo("M129 83.2L168 44.4L117 42.8Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M133.3 71L154.9 50L123.7 48.8Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Trazo("M100 49C128 49 145 59 145 81C145 103 128 117 100 117C72 117 55 103 55 81C55 59 72 49 100 49Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Grupo("ojos-abiertos", 0f, listOf(
                        Trazo("M66.5 82A12.5 12.5 0 1 0 91.5 82A12.5 12.5 0 1 0 66.5 82Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M74.4 84A4.6 7.5 0 1 0 83.6 84A4.6 7.5 0 1 0 74.4 84Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M108.5 82A12.5 12.5 0 1 0 133.5 82A12.5 12.5 0 1 0 108.5 82Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M116.4 84A4.6 7.5 0 1 0 125.6 84A4.6 7.5 0 1 0 116.4 84Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M64 65L94 65L94 76.5L64 74.5Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M136 65L106 65L106 76.5L136 74.5Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M80.6 80A2.4 2.4 0 1 0 85.4 80A2.4 2.4 0 1 0 80.6 80Z", 0xFFFFFFFF, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M122.6 80A2.4 2.4 0 1 0 127.4 80A2.4 2.4 0 1 0 122.6 80Z", 0xFFFFFFFF, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Grupo("ojos-cerrados", 1f, listOf(
                        Trazo("M67 88Q79 79 91 88M133 88Q121 79 109 88", 0x00000000, false, 0xFFE8C34A, false, 3f, 1, 1, 1f)
                    )),
                    Trazo("M95 97L105 97L100 102.5Z", 0xFF8A5252, false, 0xFF8A5252, false, 1.5f, 0, 1, 1f),
                    Trazo("M94.5 108Q100 105 105.5 108", 0x00000000, false, 0xFF6A6A6A, false, 1.6f, 1, 1, 1f),
                    Trazo("M77 103L47 100M123 103L153 100M77 107L49 112M123 107L151 112", 0x00000000, false, 0xFF6A6A6A, false, 1.3f, 1, 1, 1f)
                )),
                Grupo("cola", 1f, listOf(
                    Trazo("M146 91C134 83 100 80 74 84C62 86 53 92 56 105", 0x00000000, false, 0xFF2A2626, false, 16f, 1, 1, 1f),
                    Trazo("M146 196C176 190 184 150 168 116C160 98 152 88 138 84C116 80 84 80 64 86C54 90 51 98 56 105", 0x00000000, false, 0xFF141414, false, 13f, 1, 1, 1f)
                ))
            ))
        )))

    private fun durmiendo() = Dibujo(200f, 220f, Grupo(null, 1f, listOf(
            Grupo("contorno", 1f, listOf(
                Trazo("M44 214C36 200 40 178 60 166C82 152 128 150 150 162C170 172 174 198 164 214Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                Grupo("contorno-cabeza", 1f, listOf(
                    Trazo("M67.8 145C94.9 138.2 113.9 143.8 119.2 165.2C124.5 186.5 111.4 204.2 84.2 211C57.1 217.8 37.2 208.3 31.9 186.9C26.5 165.6 40.6 151.8 67.8 145Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                    Grupo("contorno-oreja-izq", 1f, listOf(
                        Trazo("M34 179.2L17.8 126.5L63.6 149.1Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    )),
                    Grupo("contorno-oreja-der", 1f, listOf(
                        Trazo("M113.6 159.3L103.2 105.3L73.4 146.7Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    ))
                )),
                Grupo("contorno-cola", 1f, listOf(
                    Trazo("M163 205C150 214 118 215.5 92 213.5", 0x00000000, false, 0xFF4A4444, false, 18f, 1, 1, 1f)
                ))
            )),
            Grupo("cuerpo", 1f, listOf(
                Trazo("M44 214C36 200 40 178 60 166C82 152 128 150 150 162C170 172 174 198 164 214Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M136 162C156 168 168 184 164 202C163 208 160 212 156 214L144 214C152 198 150 180 136 162Z", 0xFF2A2626, false, 0x00000000, false, 0f, 0, 0, 1f),
                Grupo("collar", 1f, listOf(
                    Trazo("M117 151Q128 176 120 203L125.5 204Q134 176 122.5 150Z", 0x00000000, true, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Grupo("cabeza", 1f, listOf(
                    Grupo("oreja-izq", 1f, listOf(
                        Trazo("M34 179.2L17.8 126.5L63.6 149.1Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M36 166.3L26.8 137.7L54.9 151.3Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Grupo("oreja-der", 1f, listOf(
                        Trazo("M113.6 159.3L103.2 105.3L73.4 146.7Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M105.9 148.9L100.5 119.3L82.1 144.5Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Trazo("M67.8 145C94.9 138.2 113.9 143.8 119.2 165.2C124.5 186.5 111.4 204.2 84.2 211C57.1 217.8 37.2 208.3 31.9 186.9C26.5 165.6 40.6 151.8 67.8 145Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Grupo("ojos-abiertos", 0f, listOf(
                        Trazo("M42.9 182.1A12.5 12.5 0 1 0 67.9 182.1A12.5 12.5 0 1 0 42.9 182.1Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M51.3 184.1A4.6 7.5 0 1 0 60.5 184.1A4.6 7.5 0 1 0 51.3 184.1Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M83.6 171.9A12.5 12.5 0 1 0 108.6 171.9A12.5 12.5 0 1 0 83.6 171.9Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M92 173.9A4.6 7.5 0 1 0 101.2 173.9A4.6 7.5 0 1 0 92 173.9Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M36.7 169.2L65.8 162L68.6 173.1L39 178.5Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M106.6 151.8L77.5 159.1L80.2 170.2L108.9 161Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M56.4 179.2A2.4 2.4 0 1 0 61.2 179.2A2.4 2.4 0 1 0 56.4 179.2Z", 0xFFFFFFFF, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M97.1 169A2.4 2.4 0 1 0 101.9 169A2.4 2.4 0 1 0 97.1 169Z", 0xFFFFFFFF, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Grupo("ojos-cerrados", 1f, listOf(
                        Trazo("M44.9 187.8Q57.8 191.8 67.3 182.2M108 172.1Q98.6 181.7 85.7 177.6", 0x00000000, false, 0xFFE8C34A, false, 3f, 1, 1, 1f)
                    )),
                    Trazo("M74.5 192.8L84.2 190.4L80.7 196.9Z", 0xFF8A5252, false, 0xFF8A5252, false, 1.5f, 0, 1, 1f),
                    Trazo("M58.5 203L28.7 207.3M103.2 191.8L131.5 181.7M59.5 206.9L33.5 218.5M104.1 195.7L132.5 193.8", 0x00000000, false, 0xFF6A6A6A, false, 1.3f, 1, 1, 1f)
                )),
                Grupo("cola", 1f, listOf(
                    Trazo("M163 205C150 214 118 215.5 92 213.5", 0x00000000, false, 0xFF2A2626, false, 14f, 1, 1, 1f),
                    Trazo("M163 205C150 214 118 215.5 92 213.5", 0x00000000, false, 0xFF141414, false, 11f, 1, 1, 1f)
                ))
            ))
        )))

    private fun jugando() = Dibujo(200f, 220f, Grupo(null, 1f, listOf(
            Grupo("contorno", 1f, listOf(
                Trazo("M70 138C56 150 44 172 44 196C44 208 50 214 60 214L140 214C150 214 156 208 156 196C156 172 144 150 130 138Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                Grupo("contorno-cola", 1f, listOf(
                    Trazo("M64 198C38 196 26 172 34 150C40 134 32 122 22 120", 0x00000000, false, 0xFF4A4444, false, 20f, 1, 1, 1f)
                )),
                Grupo("contorno-cabeza", 1f, listOf(
                    Trazo("M104.7 87.3C132.5 91.2 147.9 103.5 144.8 125.3C141.8 147.1 123 158.6 95.3 154.7C67.5 150.8 52.7 134.5 55.7 112.8C58.8 91 77 83.4 104.7 87.3Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                    Grupo("contorno-oreja-izq", 1f, listOf(
                        Trazo("M60.7 106.4L65.3 51.5L99.4 89.6Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    )),
                    Grupo("contorno-oreja-der", 1f, listOf(
                        Trazo("M141.9 117.8L152.5 63.7L109.3 91Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    ))
                )),
                Grupo("contorno-pata-der", 1f, listOf(
                    Trazo("M114 168C130 166 148 170 160 181", 0x00000000, false, 0xFF4A4444, false, 21f, 1, 1, 1f)
                ))
            )),
            Grupo("cuerpo", 1f, listOf(
                Grupo("cola", 1f, listOf(
                    Trazo("M64 198C38 196 26 172 34 150C40 134 32 122 22 120", 0x00000000, false, 0xFF141414, false, 13f, 1, 1, 1f)
                )),
                Trazo("M70 138C56 150 44 172 44 196C44 208 50 214 60 214L140 214C150 214 156 208 156 196C156 172 144 150 130 138Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M52 170C46 182 46 200 52 208C54 211 57 212.5 61 212.5L70 212.5C68 196 63 182 52 170Z", 0xFF2A2626, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M148 170C154 182 154 200 148 208C146 211 143 212.5 139 212.5L130 212.5C132 196 137 182 148 170Z", 0xFF2A2626, false, 0x00000000, false, 0f, 0, 0, 1f),
                Grupo("pata-izq", 1f, listOf(
                    Trazo("M76 160C76 180 76 196 76 206C76 211 79 214 84 214L95 214C98 214 99 212 99 209L98 160Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M87.5 196L87.5 206", 0x00000000, false, 0xFF2A2626, false, 1.3f, 1, 1, 1f)
                )),
                Grupo("collar", 1f, listOf(
                    Trazo("M76 150Q100 164 124 150L124.5 155Q100 169 75.5 155Z", 0x00000000, true, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M95 168A5 5 0 1 0 105 168A5 5 0 1 0 95 168Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Grupo("cabeza", 1f, listOf(
                    Grupo("oreja-izq", 1f, listOf(
                        Trazo("M60.7 106.4L65.3 51.5L99.4 89.6Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M67.3 95.2L69.5 65.2L90.5 88.4Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Grupo("oreja-der", 1f, listOf(
                        Trazo("M141.9 117.8L152.5 63.7L109.3 91Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M138.6 105.2L144.7 75.8L118.2 92.3Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Trazo("M104.7 87.3C132.5 91.2 147.9 103.5 144.8 125.3C141.8 147.1 123 158.6 95.3 154.7C67.5 150.8 52.7 134.5 55.7 112.8C58.8 91 77 83.4 104.7 87.3Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Grupo("ojos-abiertos", 1f, listOf(
                        Trazo("M66.8 117.1A12.5 12.5 0 1 0 91.8 117.1A12.5 12.5 0 1 0 66.8 117.1Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M77 122.5A4.6 7.5 0 1 0 86.2 122.5A4.6 7.5 0 1 0 77 122.5Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M108.4 122.9A12.5 12.5 0 1 0 133.4 122.9A12.5 12.5 0 1 0 108.4 122.9Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M118.6 128.3A4.6 7.5 0 1 0 127.8 128.3A4.6 7.5 0 1 0 118.6 128.3Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M66.9 98.2L96.6 102.3L95 113.7L65.5 107.6Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M138.2 108.2L108.4 104L106.8 115.4L136.8 117.6Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M82.2 117A2.4 2.4 0 1 0 87 117A2.4 2.4 0 1 0 82.2 117Z", 0xFFFFFFFF, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M123.8 122.9A2.4 2.4 0 1 0 128.6 122.9A2.4 2.4 0 1 0 123.8 122.9Z", 0xFFFFFFFF, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Grupo("ojos-cerrados", 0f, listOf(
                        Trazo("M67.5 118.5Q78 127 90.3 121.7M131.9 127.5Q119.5 132.8 109.1 124.3", 0x00000000, false, 0xFFE8C34A, false, 3f, 1, 1, 1f)
                    )),
                    Trazo("M93.1 134.2L103 135.6L97.3 140.3Z", 0xFF8A5252, false, 0xFF8A5252, false, 1.5f, 0, 1, 1f),
                    Trazo("M74.4 137.6L45.1 130.5M120 144L150.1 145.2M73.9 141.6L45.5 142.6M119.4 148L146.5 156.8", 0x00000000, false, 0xFF6A6A6A, false, 1.3f, 1, 1, 1f)
                )),
                Grupo("ovillo", 1f, listOf(
                    Trazo("M156 208C130 214.5 102 214 80 212.5C70 211.5 66 207 70 203.5", 0x00000000, false, 0xFF8A5252, false, 1.6f, 1, 1, 1f),
                    Trazo("M153 197A17 17 0 1 0 187 197A17 17 0 1 0 153 197Z", 0xFF8A5252, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M155 190.8Q170 200.8 185 189.5M153.8 200.8Q170 210.8 186.2 199.5M163.8 180.8Q171.2 195.8 162.5 212M175 180.8Q181.2 194.5 173.8 213.2", 0x00000000, false, 0xFFF8F0D4, false, 1.4f, 1, 1, 1f)
                )),
                Grupo("pata-der", 1f, listOf(
                    Trazo("M138 168C146 170 154 175 160 181", 0x00000000, false, 0xFF2A2626, false, 17f, 1, 1, 1f),
                    Trazo("M114 168C130 166 148 170 160 181", 0x00000000, false, 0xFF141414, false, 14f, 1, 1, 1f)
                ))
            ))
        )))

    private fun ronroneando() = Dibujo(200f, 220f, Grupo(null, 1f, listOf(
            Grupo("contorno", 1f, listOf(
                Trazo("M79 106C74 124 70 140 69 156C56 165 52 186 54 200C55 209 59 214 66 214L134 214C141 214 145 209 146 200C148 186 144 165 131 156C130 140 126 124 121 106Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                Grupo("contorno-cola", 1f, listOf(
                    Trazo("M138 206C168 206 178 182 172 158C168 142 168 128 181 123", 0x00000000, false, 0xFF4A4444, false, 20f, 1, 1, 1f)
                )),
                Grupo("contorno-cabeza", 1f, listOf(
                    Trazo("M105.7 48.3C133.5 52.2 148.9 64.5 145.8 86.3C142.8 108.1 124 119.6 96.3 115.7C68.5 111.8 53.7 95.5 56.7 73.8C59.8 52 78 44.4 105.7 48.3Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                    Grupo("contorno-oreja-izq", 1f, listOf(
                        Trazo("M63 70L60 15L99 48Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    )),
                    Grupo("contorno-oreja-der", 1f, listOf(
                        Trazo("M140.8 80.9L158.9 28.9L112.3 49.9Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    ))
                ))
            )),
            Grupo("cuerpo", 1f, listOf(
                Grupo("cola", 1f, listOf(
                    Trazo("M138 206C168 206 178 182 172 158C168 142 168 128 181 123", 0x00000000, false, 0xFF141414, false, 13f, 1, 1, 1f)
                )),
                Trazo("M79 106C74 124 70 140 69 156C56 165 52 186 54 200C55 209 59 214 66 214L134 214C141 214 145 209 146 200C148 186 144 165 131 156C130 140 126 124 121 106Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M69 159C59 167 55 185 56 199C57 207 60 211.5 66 211.5L76 211.5C74 194 74 175 69 159Z", 0xFF2A2626, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M131 159C141 167 145 185 144 199C143 207 140 211.5 134 211.5L124 211.5C126 194 126 175 131 159Z", 0xFF2A2626, false, 0x00000000, false, 0f, 0, 0, 1f),
                Grupo("pata-izq", 1f, listOf(
                    Trazo("M84 150C83.5 170 83 192 83 206C83 211 86 214 90 214L98 214C99.5 214 100 213 100 211L100 150Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M100 166L100 206", 0x00000000, false, 0xFF2A2626, false, 1.6f, 1, 1, 1f)
                )),
                Grupo("pata-der", 1f, listOf(
                    Trazo("M116 150C116.5 170 117 192 117 206C117 211 114 214 110 214L102 214C100.5 214 100 213 100 211L100 150Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Grupo("collar", 1f, listOf(
                    Trazo("M76 109Q100 123 124 109L124.5 114Q100 128 75.5 114Z", 0x00000000, true, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M95 127A5 5 0 1 0 105 127A5 5 0 1 0 95 127Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Grupo("cabeza", 1f, listOf(
                    Grupo("oreja-izq", 1f, listOf(
                        Trazo("M63 70L60 15L99 48Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M68 58L66 28L90 48Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Grupo("oreja-der", 1f, listOf(
                        Trazo("M140.8 80.9L158.9 28.9L112.3 49.9Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M139.3 68L149.5 39.7L120.9 52.3Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Trazo("M105.7 48.3C133.5 52.2 148.9 64.5 145.8 86.3C142.8 108.1 124 119.6 96.3 115.7C68.5 111.8 53.7 95.5 56.7 73.8C59.8 52 78 44.4 105.7 48.3Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Grupo("ojos-abiertos", 1f, listOf(
                        Trazo("M67.8 78.1A12.5 12.5 0 1 0 92.8 78.1A12.5 12.5 0 1 0 67.8 78.1Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M74.9 84A4.6 7.5 0 1 0 84.1 84A4.6 7.5 0 1 0 74.9 84Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M109.4 83.9A12.5 12.5 0 1 0 134.4 83.9A12.5 12.5 0 1 0 109.4 83.9Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M116.5 89.9A4.6 7.5 0 1 0 125.7 89.9A4.6 7.5 0 1 0 116.5 89.9Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M66.9 59L98.6 63.5L95.2 87.2Q81.3 71.2 63.5 82.8Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M140.1 69.3L108.5 64.9L105.1 88.6Q122.9 77 136.8 93.1Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Grupo("ojos-cerrados", 0f, listOf(
                        Trazo("M68.5 79.5Q79 88 91.3 82.7M132.9 88.5Q120.5 93.8 110.1 85.3", 0x00000000, false, 0xFFE8C34A, false, 3f, 1, 1, 1f)
                    )),
                    Trazo("M94.1 95.2L104 96.6L98.3 101.3Z", 0xFF8A5252, false, 0xFF8A5252, false, 1.5f, 0, 1, 1f),
                    Trazo("M90.8 104.3Q93.8 108.3 97.7 105.3Q100.7 109.2 104.7 106.2", 0x00000000, false, 0xFF6A6A6A, false, 1.6f, 1, 1, 1f),
                    Trazo("M75.4 98.6L46.1 91.5M121 105L151.1 106.2M74.9 102.6L46.5 103.6M120.4 109L147.5 117.8", 0x00000000, false, 0xFF6A6A6A, false, 1.3f, 1, 1, 1f)
                ))
            ))
        )))

    private fun bostezando() = Dibujo(200f, 220f, Grupo(null, 1f, listOf(
            Grupo("contorno", 1f, listOf(
                Trazo("M79 106C74 124 70 140 69 156C56 165 52 186 54 200C55 209 59 214 66 214L134 214C141 214 145 209 146 200C148 186 144 165 131 156C130 140 126 124 121 106Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                Grupo("contorno-cola", 1f, listOf(
                    Trazo("M138 206C168 206 178 182 172 158C168 142 168 128 181 123", 0x00000000, false, 0xFF4A4444, false, 20f, 1, 1, 1f)
                )),
                Grupo("contorno-cabeza", 1f, listOf(
                    Trazo("M95.3 43.3C123 39.4 141.2 47 144.3 68.8C147.3 90.5 132.5 106.8 104.7 110.7C77 114.6 58.2 103.1 55.2 81.3C52.1 59.5 67.5 47.2 95.3 43.3Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                    Trazo("M83.3 101.6C89.7 125.9 123.4 121.2 122.9 96Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                    Grupo("contorno-oreja-izq", 1f, listOf(
                        Trazo("M59.1 74.9L44.8 21.7L89.8 45.9Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    )),
                    Grupo("contorno-oreja-der", 1f, listOf(
                        Trazo("M138.7 63.7L137.9 8.6L101.3 44.3Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    ))
                ))
            )),
            Grupo("cuerpo", 1f, listOf(
                Grupo("cola", 1f, listOf(
                    Trazo("M138 206C168 206 178 182 172 158C168 142 168 128 181 123", 0x00000000, false, 0xFF141414, false, 13f, 1, 1, 1f)
                )),
                Trazo("M79 106C74 124 70 140 69 156C56 165 52 186 54 200C55 209 59 214 66 214L134 214C141 214 145 209 146 200C148 186 144 165 131 156C130 140 126 124 121 106Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M69 159C59 167 55 185 56 199C57 207 60 211.5 66 211.5L76 211.5C74 194 74 175 69 159Z", 0xFF2A2626, false, 0x00000000, false, 0f, 0, 0, 1f),
                Trazo("M131 159C141 167 145 185 144 199C143 207 140 211.5 134 211.5L124 211.5C126 194 126 175 131 159Z", 0xFF2A2626, false, 0x00000000, false, 0f, 0, 0, 1f),
                Grupo("pata-izq", 1f, listOf(
                    Trazo("M84 150C83.5 170 83 192 83 206C83 211 86 214 90 214L98 214C99.5 214 100 213 100 211L100 150Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M100 166L100 206", 0x00000000, false, 0xFF2A2626, false, 1.6f, 1, 1, 1f)
                )),
                Grupo("pata-der", 1f, listOf(
                    Trazo("M116 150C116.5 170 117 192 117 206C117 211 114 214 110 214L102 214C100.5 214 100 213 100 211L100 150Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Grupo("collar", 1f, listOf(
                    Trazo("M76 109Q100 123 124 109L124.5 114Q100 128 75.5 114Z", 0x00000000, true, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M95 127A5 5 0 1 0 105 127A5 5 0 1 0 95 127Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Grupo("cabeza", 1f, listOf(
                    Grupo("oreja-izq", 1f, listOf(
                        Trazo("M59.1 74.9L44.8 21.7L89.8 45.9Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M61.5 62.1L53.3 33.2L81 47.8Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Grupo("oreja-der", 1f, listOf(
                        Trazo("M138.7 63.7L137.9 8.6L101.3 44.3Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                        Trazo("M132.9 52.1L132.8 22L110.2 43.6Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Trazo("M95.3 43.3C123 39.4 141.2 47 144.3 68.8C147.3 90.5 132.5 106.8 104.7 110.7C77 114.6 58.2 103.1 55.2 81.3C52.1 59.5 67.5 47.2 95.3 43.3Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M83.3 101.6C89.7 125.9 123.4 121.2 122.9 96Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Grupo("ojos-abiertos", 0f, listOf(
                        Trazo("M66.6 78.9A12.5 12.5 0 1 0 91.6 78.9A12.5 12.5 0 1 0 66.6 78.9Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M74.7 80.9A4.6 7.5 0 1 0 83.9 80.9A4.6 7.5 0 1 0 74.7 80.9Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M108.2 73.1A12.5 12.5 0 1 0 133.2 73.1A12.5 12.5 0 1 0 108.2 73.1Z", 0xFFE8C34A, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M116.3 75.1A4.6 7.5 0 1 0 125.5 75.1A4.6 7.5 0 1 0 116.3 75.1Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M61.8 64.2L91.6 60L93.2 71.4L63.2 73.6Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M133.1 54.2L103.4 58.3L105 69.7L134.5 63.6Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M80.3 76.4A2.4 2.4 0 1 0 85.1 76.4A2.4 2.4 0 1 0 80.3 76.4Z", 0xFFFFFFFF, false, 0x00000000, false, 0f, 0, 0, 1f),
                        Trazo("M121.9 70.5A2.4 2.4 0 1 0 126.7 70.5A2.4 2.4 0 1 0 121.9 70.5Z", 0xFFFFFFFF, false, 0x00000000, false, 0f, 0, 0, 1f)
                    )),
                    Grupo("ojos-cerrados", 1f, listOf(
                        Trazo("M68 86.5Q78.6 76 91.8 83.2M133.4 77.4Q120.2 70.1 109.6 80.7", 0x00000000, false, 0xFFE8C34A, false, 3f, 1, 1, 1f)
                    )),
                    Trazo("M91.6 96.4C90.9 91.4 112.7 88.3 113.4 93.3C115.5 108.1 111.6 116.8 105.7 117.6C99.8 118.4 93.7 111.2 91.6 96.4Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M97.3 111.7C99.2 106.9 109.1 105.5 112.2 109.6C110.4 114.9 100.5 116.3 97.3 111.7Z", 0xFF8A5252, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M93.4 94.6L96.4 94.2L95.4 97.8ZM108.2 92.5L111.2 92.1L110.2 95.8Z", 0xFFF8F0D4, false, 0x00000000, false, 0f, 0, 0, 1f),
                    Trazo("M96.8 86.5L105.7 85.3L101.9 90.9Z", 0xFF8A5252, false, 0xFF8A5252, false, 1.5f, 0, 1, 1f),
                    Trazo("M80 100L49.9 101.2M125.6 93.6L154.9 86.5M80.6 104L53.5 112.8M126.1 97.6L154.5 98.6", 0x00000000, false, 0xFF6A6A6A, false, 1.3f, 1, 1, 1f)
                ))
            ))
        )))

    private fun escondido() = Dibujo(200f, 80f, Grupo(null, 1f, listOf(
            Grupo("contorno", 1f, listOf(
                Grupo("contorno-cabeza", 1f, listOf(
                    Trazo("M100 55C128 55 145 65 145 87C145 109 128 123 100 123C72 123 55 109 55 87C55 65 72 55 100 55Z", 0xFF4A4444, false, 0xFF4A4444, false, 7f, 0, 1, 1f),
                    Grupo("contorno-oreja-izq", 1f, listOf(
                        Trazo("M59 80L56 25L95 58Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    )),
                    Grupo("contorno-oreja-der", 1f, listOf(
                        Trazo("M141 80L144 25L105 58Z", 0xFF4A4444, false, 0xFF4A4444, false, 11f, 0, 1, 1f)
                    ))
                ))
            )),
            Grupo("cabeza", 1f, listOf(
                Grupo("oreja-izq", 1f, listOf(
                    Trazo("M59 80L56 25L95 58Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                    Trazo("M64 68L62 38L86 58Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Grupo("oreja-der", 1f, listOf(
                    Trazo("M141 80L144 25L105 58Z", 0xFF141414, false, 0xFF141414, false, 4f, 0, 1, 1f),
                    Trazo("M136 68L138 38L114 58Z", 0xFF3A2C2C, false, 0x00000000, false, 0f, 0, 0, 1f)
                )),
                Trazo("M100 55C128 55 145 65 145 87C145 109 128 123 100 123C72 123 55 109 55 87C55 65 72 55 100 55Z", 0xFF141414, false, 0x00000000, false, 0f, 0, 0, 1f)
            ))
        )))
}
