# Social Media Timer

(Warning: This app was developed primarily using AI and may contain errors.)

Eine Android-App, die die Nutzungszeit von Social-Media-Apps begrenzt: Nach einer festgelegten Zeit *echter* Nutzung folgt eine Pause (Cooldown), danach beginnt der Zyklus bei der nächsten Nutzung von vorn – beliebig oft, im Dauerbetrieb.

Die App läuft komplett lokal auf dem Gerät. Das eigentliche Sperren der Apps übernimmt **„Modi und Routinen“ von Samsung**, gesteuert über Benachrichtigungen dieser App.

## Funktionsweise

Pro überwachter App gibt es zwei Timer:

1. **Nutzungs-Timer** – zählt nur, solange die App wirklich genutzt wird:
   - der Bildschirm ist an **und** das Handy ist entsperrt,
   - die App ist sichtbar im Vordergrund.

   In allen anderen Fällen pausiert er, z. B. bei ausgeschaltetem Bildschirm (auch wenn die App noch offen ist), auf dem Sperrbildschirm, oder wenn die App minimiert bzw. im Hintergrund ist. Die bereits genutzte Zeit bleibt dabei erhalten. Wird die App so lange nicht genutzt, wie der Cooldown dauert, verfällt die angebrochene Zeit und das Limit beginnt von vorn.
2. **Cooldown-Timer** – startet, wenn die Nutzungszeit aufgebraucht ist. Er läuft nach Uhrzeit, unabhängig davon, ob das Handy benutzt wird. Solange er läuft, ist die App gesperrt.

Ablauf eines Zyklus:

```
App genutzt ───► Nutzungszeit aufgebraucht ──► Benachrichtigung „<App>-Zeit abgelaufen“
                                                  │  (Routine aktiviert Sperr-Modus)
                                                  ▼
                                          Cooldown läuft
                                                  │
                                                  ▼
                                   Benachrichtigung „<App> wieder freigegeben“
                                                  │  (Routine beendet Sperr-Modus)
                                                  ▼
                               Bereit – nächste Nutzung startet einen neuen Zyklus
```

Welche App gerade im Vordergrund ist, erkennt die App über einen Bedienungshilfen-Dienst. Dabei wird nur der Name der sichtbaren App ermittelt; Bildschirminhalte werden nicht gelesen oder gespeichert.

Geht ein Alarm verloren (z. B. durch Neustart, App-Update oder Energiesparmaßnahmen), holt die App verpasste Übergänge automatisch nach, sobald die überwachte App oder Social Timer selbst geöffnet wird. Ein Zyklus bleibt dadurch nicht dauerhaft hängen.

## Voraussetzungen

- Android 14 oder neuer
- Samsung-Gerät mit „Modi und Routinen“ (für das Sperren der Apps)

## Einrichtung

### 1. Apps hinzufügen

In Social Timer über **+** eine App auswählen und Nutzungszeit sowie Cooldown in Minuten festlegen.

### 2. Berechtigungen erteilen

Fehlende Berechtigungen zeigt die App als rote Hinweise in der App-Liste an. Benötigt werden:

- **Bedienungshilfen-Dienst** „Social Timer“ – erkennt, ob eine überwachte App gerade genutzt wird
- **Benachrichtigungen** – lösen die Routinen aus
- **Exakte Alarme** – damit Timer pünktlich ablaufen
- **Ausnahme von der Akku-Optimierung** – siehe unten

### 3. Energiesparmodus / Akku-Einstellungen

Samsungs Energiesparfunktionen können Alarme verzögern oder die App im Hintergrund beenden. Dann kommen die Benachrichtigungen zu spät oder gar nicht. Bitte zusätzlich von Hand einstellen:

- **Einstellungen → Apps → Social Timer → Akku → „Nicht eingeschränkt“**
- **Einstellungen → Akku → Hintergrund-Nutzungslimits:** Social Timer darf weder bei **„Schlafende Apps“** noch bei **„Tief schlafende Apps“** stehen.

### 4. Routinen in „Modi und Routinen“ anlegen

Für jede überwachte App werden zwei Routinen benötigt:

| Routine | Bedingung (Wenn) | Aktion (Dann) |
|---|---|---|
| Sperren | Benachrichtigung von Social Timer, Text enthält „Zeit abgelaufen“ | Modus aktivieren, der die App blockiert |
| Freigeben | Benachrichtigung von Social Timer, Text enthält „wieder freigegeben“ | Sperr-Modus beenden |

Die Benachrichtigungen haben die Form **„&lt;App&gt;-Zeit abgelaufen“** bzw. **„&lt;App&gt; wieder freigegeben“** (z. B. „Instagram-Zeit abgelaufen“). Bei mehreren Apps kann die Bedingung auf den App-Namen eingeschränkt werden.

## Projekt bauen

Das Projekt ist ein Gradle-Projekt (Kotlin, Jetpack Compose, Room) und lässt sich direkt in Android Studio öffnen und auf dem Gerät installieren.
