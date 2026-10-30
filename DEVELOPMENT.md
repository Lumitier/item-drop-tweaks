# Entwicklungsnotizen

Interne Doku. Nicht fuer Nutzer gedacht — die lesen die `README.md`.

## Versionen

Build-Kette identisch zu Perpetual Snow und Experience Orb Tweaks.

| Baustein | Version | Woher |
|---|---|---|
| Minecraft | 1.21.11 | — |
| Yarn | 1.21.11+build.6 | hoechster Release-Build |
| Fabric Loader | 0.20.1 | |
| Loom | 1.19.3 | braucht Gradle >= 9.8.0 **und JVM 25** |
| Gradle | 9.8.2 | |
| MixinExtras | 0.5.5 | compileOnly + annotationProcessor + include |
| YACL | 3.8.2+1.21.11-fabric (`pHWDw3Vc`) | Modrinth-Version-ID |
| Mod Menu | 17.0.1 (`cLkYaVuL`) | Modrinth-Version-ID |
| quilt-parsers | 0.2.1 | YACLs versteckte Laufzeit-Abhaengigkeit |

**fabric-api** steht bewusst nicht in `depends`: Wird nirgends benutzt,
YACL bringt seine eigene Deklaration mit.

## Die Falle, die am meisten Zeit gekostet hat

`ItemEntityRenderer` enthaelt **zwei Methoden mit identischer Kopien-Logik**:

| Methode | Wird aus dem Item-Entity-Pfad aufgerufen? |
|---|---|
| `renderStack(MatrixStack, queue, int, ItemStackEntityRenderState, Random)` | **NEIN** |
| `render(MatrixStack, queue, int, ItemStackEntityRenderState, Random, Box)` | ja |

`renderStack` ist ein oeffentlicher Helfer fuer anderen Code. Ein Hook dort
**greift sauber und bleibt trotzdem wirkungslos** — kein Crash, kein
Hinweis, auch nicht mit `injectors.defaultRequire = 1`, weil die Konstante
ja gefunden wird.

**Merke:** Bevor ein Hook gesetzt wird, pruefen ob die Zielmethode im
tatsaechlichen Aufrufpfad liegt:

```
javap -c -p -classpath <jar> <Klasse> | grep "invokestatic.*<Methode>"
```

Kein Treffer heisst: die Methode ruft niemand auf.

## Architektur

Zwei Mixins, beide client-only.

### `ItemEntityRendererMixin`

| Hook | Ziel | Steuert |
|---|---|---|
| `@Inject` HEAD, cancellable | Instanz-`render` | Items ausblenden |
| `@Inject` RETURN | `updateRenderState` | Stapelgroesse, Kopienzahl, Schatten, Eigenleuchten |
| `@Redirect` | 6-Arg-`render`-Aufruf in der Instanz-`render` | Groesse (eigenes push/scale/pop) |
| `@Redirect` | `ItemEntity.getRotation(FF)F` | Drehgeschwindigkeit |
| `@ModifyConstant` 10.0F | Instanz-`render` | Schwebegeschwindigkeit |
| `@ModifyConstant` 0.1F, ordinal 0 | Instanz-`render` | Schwebehoehe (Amplitude) |
| `@ModifyConstant` 0.15F | **6-Arg-`render`** | Streuung der Kopien |

### `ItemStackEntityRenderStateMixin`

Haengt per `@Unique` ein `int`-Feld an den RenderState, zugaenglich ueber
`com.lumitier.itemdroptweaks.duck.ItemCountHolder`. Keine Injektion in
Vanilla-Code, daher kollisionsfrei.

## Vanilla-Code, gegen den gebaut wurde

```java
// Instanz-render
matrices.push();
float y = -box.minY + 0.0625F + sin(age / 10.0F + uniqueOffset) * 0.1F + 0.1F;
matrices.translate(0, y, 0);
matrices.multiply(rotation(ItemEntity.getRotation(age, uniqueOffset)));
render(matrices, queue, state.light, state, random, box);   // 6-Arg, statisch
matrices.pop();
super.render(...);

// 6-Arg-render, Zweig fuer 3D-Modelle (Box-Tiefe > 0.0625F)
irs.render(...);                                   // erste Kopie
for (int i = 1; i < state.renderedAmount; i++) {
    matrices.push();
    matrices.translate((rnd*2-1)*0.15F, (rnd*2-1)*0.15F, (rnd*2-1)*0.15F);
    irs.render(...);
    matrices.pop();
}
// Zweig fuer flache Sprites: in Z gestaffelt (Tiefe * 1.5F),
// seitlicher Versatz mit 0.15F * 0.5F
```

`0.15F` kommt in der 6-Arg-`render` **fuenfmal** vor — dreimal im
3D-Zweig, zweimal im Sprite-Zweig. Ohne `ordinal` werden alle getroffen,
und die Option wirkt dadurch auch bei flachen Items.

`0.1F` kommt in der Instanz-`render` **zweimal** vor: als Amplitude und als
fester Sockel. `ordinal = 0` trifft die Amplitude — praktisch bestaetigt.

## Die Luecke, die die Mod schliesst

```java
static int getRenderedAmount(int count) {   // ItemStackEntityRenderState
    if (count <=  1) return 1;
    if (count <= 16) return 2;
    if (count <= 32) return 3;
    if (count <= 48) return 4;
    return 5;                                // Deckel
}
```

Ab 49 Items sieht jeder Haufen gleich aus. `ItemEntity.getStack()` ist
public und der Stack wird per TrackedData gesynct — der Client kennt die
echte Anzahl, Vanilla nutzt sie beim Rendern nur nicht aus.

Die Skala ist **logarithmisch** (`log(count) / log(countMax)`). Linear waere
bei Merge-Mod-Mengen unbrauchbar.

Ohne Merge-Mod traegt ein ItemEntity hoechstens einen vollen Stapel. Die
Optionen fallen dann kaum auf — das gehoert so auf die Projektseite.

## Eigenleuchten

```java
state.light = Math.max(state.light, LightmapTextureManager.pack(g, g));
```

`max` auf dem gepackten Lichtwert ist nicht kanalweise korrekt, aber
monoton: es hellt nur auf und dunkelt nie ab. Bei `glow = 0` passiert
nichts.

## Wenn Minecraft aktualisiert wird

Reihenfolge beim Portieren, nach Zerbrechlichkeit sortiert:

1. `gradle.properties` auf neue Yarn-/Loader-Versionen
2. **Zuerst pruefen:** Heisst die 6-Arg-`render` noch so und liegt sie noch
   im Aufrufpfad? Mit `grep "invokestatic.*render"` gegenpruefen.
3. Stehen `0.15F`, `0.1F`, `10.0F` noch an denselben Stellen? Vorkommen
   **zaehlen**, nicht annehmen — die Ordinals haengen daran.
4. `getRotation(FF)F` noch vorhanden?
5. `getRenderedAmount` noch mit denselben Schwellen?
6. `shadowRadius` und `light` noch public im RenderState?

## Build

```
.\gradlew build
```

Ergebnis: `build\libs\itemdroptweaks-<version>.jar` (die **ohne**
`-sources`). Die Jar buendelt MixinExtras und quilt-parsers per
jar-in-jar; `LICENSE` und `NOTICE` liegen mit drin.
