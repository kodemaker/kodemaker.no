:title Python og ytelse
:author eivind
:tech [:opencv :python :performance]
:published 2020-02-19

:blurb

De siste årene har jeg brukt Python sammen med OpenCV til å implementere en del
bildeanalyse. Prosessering av bilder kan fort bli litt krevende for datamaskinen -
og når man vil behandle mange bilder på kort tid er det viktig å skrive effektiv
kode. Her beskriver jeg en teknikk jeg har brukt for å se på Python-kode og
effektivitet.

:body

I noen typer utvikling spiller det fortsatt en rolle hvor effektiv hver linje
faktisk er. Et slikt område er bildeanalyse - hvor samme operasjon kanskje skal
gjentaes på 1 million punkter - eller man skal behandle mange bilder pr. sekund fra
en videostrøm. Jeg har tidligere skrevet litt om hvordan man kan gjøre slik
bildeanalyse i Python og OpenCV (
[Del 1 - Intro](/blogg/2019-09-bildeanalyse-intro/),
[Del 2 - Linjer](/blogg/2019-09-bildeanalyse-linjer/),
[Del 3 - Objekter](/blogg/2020-01-bildeanalyse-objekter/)) - og i dag tenkte jeg å
vise hvordan jeg går frem for å gjøre koden mest mulig effektiv.

## Eksempel

For å illustrere bruker jeg kode fra mine tidligere skriverier om linjer og
objekter. Her har vi litt kode som finner linjer i et bilde, tar et utsnitt,
rensker bort støy og finner + tegner objekter:

```python
def run():
  img = cv.imread("bordet.jpg", cv.IMREAD_UNCHANGED)
  height, width, _ = img.shape

  lines = findLines(img)
  isects = findIntersections(img, lines)

  mask = np.zeros([height, width], dtype = np.uint8)
  cv.fillPoly(mask, np.array([isects], np.int32), 255)
  maskedImg = cv.bitwise_and(img, img, mask=mask)
  imgGray = cv.cvtColor(maskedImg, cv.COLOR_BGR2GRAY)
  edges = cv.Canny(imgGray, 20, 150)
  th = cv.adaptiveThreshold(imgGray, 255, cv.ADAPTIVE_THRESH_GAUSSIAN_C, cv.THRESH_BINARY, 11, 2)

  strEl = cv.getStructuringElement(cv.MORPH_ELLIPSE, (30, 30))
  closed = cv.morphologyEx(edges, cv.MORPH_CLOSE, strEl)
  strEl2 = cv.getStructuringElement(cv.MORPH_ELLIPSE, (5, 5))
  opened = cv.morphologyEx(closed, cv.MORPH_OPEN, strEl2)

  cnts, hierarchy = cv.findContours(opened, cv.RETR_EXTERNAL, cv.CHAIN_APPROX_SIMPLE)
  largeCnts = [cnt for cnt in cnts if cv.contourArea(cnt) > 1000]

  cv.polylines(maskedImg, np.array([isects], np.int32), True, (0, 0, 255), 2)
  cv.drawContours(img, largeCnts, -1, (255, 0, 0), 2, cv.LINE_AA)

  print("Found {} contours".format(len(largeCnts)))

  return [opened, closed, edges, img]
```

La oss si at denne greia skulle kjøres konstant på en strøm av bilder - og den tok
litt lang tid. Så nå ønsker jeg å finne ut hvor jeg bør starte om jeg vil ha litt
kjappere behandling. Jeg har opplevd at det sjelden er helt intuitivt hvilken del
av koden som bruker mest tid - men har funnet et fint verktøy for å hjelpe meg.

## Python line_profiler

For noen år siden kom jeg over [line_profiler](https://github.com/rkern/line_profiler).
Det er et glimrende lite verktøy som kan måle hvor lang tid hver linje i en funksjon
bruker. Etter å ha installert line_profiler (`pip install line_profiler`) får man
tilgang til et kommandolinje-verktøy som heter `kernprof` som brukes i stedet for
`python` når man skal kjøre koden. Da vil den lage en profil av alle funksjoner som
har en `@profile` annotasjon. Så for å se på eksempelkoden over:

1. Sette inn annotasjon:
```python
@profile
def run():
  img = ...
```

2. Kjøre med line_profiler (fila heter `bord.py`):
```bash
kernprof -v -l bord.py
```

Programmet spytter ut hele koden til `run`-funksjonen med målte tider:

```
Total time: 0.139813 s
File: bord.py
Function: run at line 85

Line #      Hits         Time  Per Hit   % Time  Line Contents
==============================================================
    85                                           @profile
    86                                           def run():
    87         1      15181.0  15181.0     10.9    img = cv.imread("bordet.jpg", cv.IMREAD_UNCHANGED)
    88         1          5.0      5.0      0.0    height, width, _ = img.shape
    89
    90         1      81337.0  81337.0     58.2    lines = findLines(img)
    91         1         66.0     66.0      0.0    isects = findIntersections(img, lines)
    92
    93         1         58.0     58.0      0.0    mask = np.zeros([height, width], dtype = np.uint8)
    94         1         97.0     97.0      0.1    cv.fillPoly(mask, np.array([isects], np.int32), 255)
    95         1       2260.0   2260.0      1.6    maskedImg = cv.bitwise_and(img, img, mask=mask)
    96         1        307.0    307.0      0.2    imgGray = cv.cvtColor(maskedImg, cv.COLOR_BGR2GRAY)
    97         1       2665.0   2665.0      1.9    edges = cv.Canny(imgGray, 20, 150)
    98         1       5543.0   5543.0      4.0    th = cv.adaptiveThreshold(imgGray, 255, cv.ADAPTIVE_THRESH_GAUSSIAN_C, cv.THRESH_BINARY, 11, 2)
    99
   100         1         23.0     23.0      0.0    strEl = cv.getStructuringElement(cv.MORPH_ELLIPSE, (30, 30))
   101         1      26748.0  26748.0     19.1    closed = cv.morphologyEx(edges, cv.MORPH_CLOSE, strEl)
   102         1         33.0     33.0      0.0    strEl2 = cv.getStructuringElement(cv.MORPH_ELLIPSE, (5, 5))
   103         1       1197.0   1197.0      0.9    opened = cv.morphologyEx(closed, cv.MORPH_OPEN, strEl2)
   104
   105         1        783.0    783.0      0.6    cnts, hierarchy = cv.findContours(opened, cv.RETR_EXTERNAL, cv.CHAIN_APPROX_SIMPLE)
   106         1         31.0     31.0      0.0    largeCnts = [cnt for cnt in cnts if cv.contourArea(cnt) > 1000]
   107
   108         1        251.0    251.0      0.2    cv.polylines(maskedImg, np.array([isects], np.int32), True, (0, 0, 255), 2)
   109         1       3199.0   3199.0      2.3    cv.drawContours(img, largeCnts, -1, (255, 0, 0), 2, cv.LINE_AA)
   110
   111         1         28.0     28.0      0.0    print("Found {} contours".format(len(largeCnts)))
   112
   113         1          1.0      1.0      0.0    return [opened, closed, edges, img]
```

Det jeg stort sett har fokusert mest på er `% time` som altså viser hvor stor andel
av den totale kjøretiden som skjedde på akkurat denne linjen. Vi ser på output over
at 58.2% av tiden gikk til å finne linjer - og 19.1% på å gjøre EN morphological
close operasjon. Så dette burde være gode kandidater om jeg trenger å trimme ned
tiden litt.

Det som er litt snedig er at morphological open og close er omtrent samme type
operasjon - allikevel tar close her 19.1% og open bare 0.9%. Forskjellen ligger kun
i størrelsen på strukturelementet som benyttes. Close bruker et 30x30 pixel ellipse,
mens open bare bruker 5x5. La oss se hva som skjer hvis man bruker 5x5 på close også:

```
   100         1         20.0     20.0      0.0    strEl = cv.getStructuringElement(cv.MORPH_ELLIPSE, (5, 5))
   101         1       1261.0   1261.0      1.1    closed = cv.morphologyEx(edges, cv.MORPH_CLOSE, strEl)
   102         1          6.0      6.0      0.0    strEl2 = cv.getStructuringElement(cv.MORPH_ELLIPSE, (5, 5))
   103         1       1262.0   1262.0      1.1    opened = cv.morphologyEx(closed, cv.MORPH_OPEN, strEl2)
```

Dette er ganske logisk om man kan litt om bildeanalyse (~900 operasjoner tar lengre
tid enn ~25 når de skal kjøres på 1.25 millioner pixler), men det er ikke alltid
like lett å peke ut "synderen" når et stykke kode bruker for lang tid.

## Final words...

Python er et språk som er svært lett å profilere. Jeg har brukt line_profiler, men
det finnes masse andre verktøy som gjør litt lignende greier. Tidligere har jeg
forsøkt å gjøre lignende analyser på Java-kode - og det er noe mer vrient. Det er
vel neppe noen som gidder å bruke Java til bildeanalyse, men her har du i hvertfall
et godt argument for å bruke Python til ting hvor du trenger god kontroll på
ytelsen til hver kodelinje. Om noe skulle gå litt treigt er det veldig fort gjort å
finne ut hvorfor!
