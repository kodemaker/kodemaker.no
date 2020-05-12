:title Clojure zippers - snasen traversering og manipulering av trær
:published 2020-05-13
:author magnus
:tech [:clojure :functional-programming]

:blurb

Har du hørt om zippers før? Ikke glidlåser altså, men en teknikk for å representere aggregerte datastrukturer slik at de kan enkelt traverseres og manipuleres nærmest vilkårlig.
La oss utforske zippers litt nærmere og se hvordan du kan bruke de i Clojure.


:body

> A zipper is a technique of representing an aggregate data structure so that it is convenient for writing programs that traverse the structure arbitrarily and update its contents, especially in purely functional programming languages (wikipedia).

Man kan tenke på en zipper som en to-element tuple, hvor det første elementet er en trelignende datastruktur og det andre elementet
er en slags peker til hvor du er i datastrukturen.

Zippers er jo selvfølgelig ikke språkavhengig, men egner seg best i funksjonelle språk som har ikke-muterbare datastrukturer.
Clojure er et slikt språk, og det fiffige er at det følger med en implementasjon rett ut av boksen. Du finner det API'et i navnerommet `clojure.zip`.



## Opprette en zipper
```clojure

;; Vi trekker in navnerommet for å jobbe med zippers i Clojure
(require '[clojure.zip :as zip])

;; Opprett en zipper for en vektor-basert datastruktur
(def simple-root (zip/vector-zip [1 2 3]))
```

## Navigere med en zipper
Du kan enkelt navigere datastrukturen ved intuitive funksjonsnavn som `left` `right` `up` `down` m.fl.
```clojure
(-> simple-root zip/down) ; => [1 {:l [], :pnodes [[1 2 3]], :ppath nil, :r (2 3)}]
(-> simple-root zip/down zip/right) ; => [2 {:l [1], :pnodes [[1 2 3]], :ppath nil, :r (3)}]
(-> simple-root zip/down zip/rightmost) ; => [3 {:l [1 2], :pnodes [[1 2 3]], :ppath nil, :r nil}]
```

I første eksempel så flytter vi oss ned fra rotnoden, vår zipper eller lokasjon om du vil (ofte navngitt zloc) peker nå til `1`.
Så `down` velger altså noden lengst til venstre. Du kan se det i resultatet. Vi har en 2-tuppel, det første elementet er noden vi nå er på og det andre elementet er en implementasjonsdetalj som holder orden på datastrukturen og stien til den noden vi er på.

I andre eksempel så flytter vi oss ned og så til høyre. Da er vi ved `2` noden.
I det siste eksemplet bruker vi `rightmost`-funksjonen for å flytte oss til noden lengst til høyre i listen med søskennoder.


Dersom du ønsker å bare se på gjeldende node kan du bruke `node`-funksjonen.
```clojure
(-> simple-root zip/down zip/node) ; => 1
```

> Pass på å ikke ramle utenfor!
```clojure
(-> simple-root zip/down zip/down) ; => nil
(-> simple-root zip/up) ; => nil
```
Det er fort gjort å ramle ut av treet dersom man ikke passer på. I det første eksemplet navigerer vi først til `1` noden og prøver så å gå ned en gang til. `1` er en løvnode, så da får vi `nil` tilbake. Vi har ramlet ut i det store intet!
Dette er per design, men veldig greit å være klar over.

## Navigere som en pro med `next`
Funksjonen `next` lar deg navigere med en dybde-først strategi. Den er også litt hyggeligere en andre navigeringsfunksjoner da du ikke ramler ut.

```clojure
(-> simple-root zip/next) ; => 1
(-> simple-root zip/next zip/next zip/next zip/next zip/node) ; => 3       - Huh?
(-> simple-root zip/next zip/next zip/next zip/next zip/end?) ; => true    - Aha!

;; Du kan enkelt iterere over noder med standard Clojure-funksjoner
(->> simple-root
     (iterate zip/next) ; iterer (potensielt uendelig) med next funksjonen
     (take-while (complement zip/end?)) ; fortsett til du har kommet til jordens/treets ende
     (map zip/node)) ; For alle lokasjoner, hent ut gjeldende node
     ; => ([1 2 3] 1 2 3)
```

## Manipulere noder i treet

```clojure

;; Legg til en undernode, den legges sist i listen av søskennodene
(-> simple-root (zip/append-child 4) z/node) ; => [1 2 3 4]

;; Legg til en søskennode til høyre for gjeldende `loc`
(-> simple-root zip/down zip/rightmost (zip/insert-right 4) zip/up zip/node) ; => [1 2 3 4]

;; Legg til en søskennode til venstre for gjeldende
(-> simple-root zip/down (zip/insert-left 0) zip/up zip/node) ; => [0 1 2 3]

;; Erstatt verdien til gjeldende node
(-> simple-root zip/down (zip/replace 100) z/up zip/node) ; => [100 2 3]

;; `edit` tar en funksjon som første parameter. Denne funksjonen mottar gjeldende verdi som input parameter.
;; I dette tilfellet inkrementerer vi bare verdien
(-> simple-root zip/down (zip/edit inc) z/up zip/node) ; => [2 2 3]

;; Verdt å merke seg er når man fjerner gjeldende node med `remove` flytter lokasjon seg til forrige node gitt en dypde-først traversering
(-> simple-root zip/next zip/next zip/remove zip/node) ; => 1

;; Som du ser, når du så går opp et hakk, så er verdien `2` borte.
(-> simple-root zip/next zip/next zip/remove zip/up zip/node) ; => [1 3]

```

## Skreddersydd zipper?
Ut av boksen støttes zippers for `vector`(`vector-zip`) og `seq`(`seq-zip`). Hva om du har en miks av begge eller noe annet som trenger skreddersøm? Fortvil ikke, `zipper`-funksjonen kan fort være noe for deg!

```clojure
(def custom-root (zip/zipper sequential?
                             seq
                             (fn [_ c] c)
                             [[1 2] '(3 4) (map inc [4 5])]))

```
Funksjonen zipper tar 4 argumenter
1. Første argument er en predikatfunksjon som bestemmer om noden kan ha undernoder (dvs om det er en grennode). I vårt tilfelle så vil både vektor og lister være omfattet.
2. Andre argument er en funksjon som gitt en grennode, returnerer dens undernoder/barn som en sekvens. I vårt tilfelle bruker vi bare `seq`-funksjonen (som funker på både lister og vektorer).
3. Tredje argument er en funksjon som lager en node, den får inn en eksisterende node og dens barn som argumenter og skal returnere en grennode med de angitte undernodene. I vårt tilfelle returnerer vi bare undernodene slik de er.
4. Fjerde argument er rotnoden, dvs datastrukturen vi ønsker å zipifisere!

```clojure
;; Ikke så mye nytt her, utover at vi går 2 nivåer ned
(-> custom-root zip/next zip/next zip/node) ; => 1

;; Funker fint når vi navigerer til barn av grennoden som er en liste
(-> custom-root zip/next zip/next zip/next zip/next zip/next zip/node) ; => 3
(-> custom-root zip/next zip/next zip/next zip/next zip/node) ; => (3 4)

;; Noden som representerer grenen for vårt map statement. Ser ut som en vanlig liste.
(-> custom-root zip/down zip/rightmost zip/node) ; => (5 6)

;; Under panseret så skinner Clojure sin hang for lazyness igennom.
;; Vi har faktisk en `LazySeq`
(-> custom-root zip/down zip/rightmost zip/node type) ; => Clojure.lang.LazySeq
```

Denne funksjonen er litt å bite over, men når du først får taket på den så klarer man å representere veldig mange skreddersydde datastrukturer.


### Hva var det jeg skulle bruke dette til sa du?
Zippers er antageligvis ikke et verktøy du kommer til å bruke veldig mye, men kan vise seg å være svært nyttig når du trenger å  navigere eller manipulere tre-lignende datastrukturer.
Kanskje du har en fiffig algoritme som trenger å navigere (og kanskje manipulere) en nøstet datastruktur som ligner på et tre?
Dersom det også viser seg å bli klønete med nøsta `map/mapcat/update-in etc` så kan det hende at zippers er verdt en kjapp vurdering i det minste.

Noen nevneverdige eksempler hvor zippers er brukt er;  xml/xhtml prosessering med f.eks [clojure.data.zip](https://github.com/clojure/data.zip) biblioteket, eller traversering/manipulering av kildekode med [rewrite-clj](https://github.com/xsc/rewrite-clj).


#### En smakebit av rewrite-clj
Dette biblioteket er kanskje ikke så godt kjent, men er flittig i bruk av mange andre Clojure biblioteker.
Det lar deg enkelt traversere og manipulere Clojure(/script) kode eller edn-filer uten å tulle til whitespace og kommentarer.
Kode er jo ypperlig å representere som en trestruktur, og lispkode er jo nærmest en perfekt match.


La oss si at jeg har en `deps.edn` fil som følger;
```clojure
{:deps
 {;; Rewrite Clojure/edn code
  rewrite-clj {:mvn/version "0.6.1"}
  ;; Customizable Clojure code formatter
  cljfmt {:mvn/version "0.6.4"}}}
```

En ny versjon av `rewrite-clj` har kommet og du som programmerer ville jo aldri funnet på å åpne en teksteditor for å fikse versjonnummeret?
Det mest rasjonelle ville jo vært å bare fyre opp et repl og gjøre noe ala:

```clojure
(require '[rewrite.clj.zip :as z])

(let [zloc (z/of-file "deps.edn")] ; Hendig funksjon for å zippifisere innholdet av en edn/clojure/clojurescript fil
  (-> zloc
      (z/find-value z/next 'rewrite-clj) ; Finn noden hvor rewrite-clj er nøkkel
      z/right ; Gå til verdi-noden
      (z/find-value z/next :mvn/version) ; Let etter noden med nøkkel for versjonsnr
      z/right ; gå til noden som inneholder versjonnr
      (z/replace "0.6.2") ; Erstatt med ny versjon
      z/print-root)) ; Sjekk ut resultatet, evt skriv til fil

; =>
{:deps
 {;; Rewrite Clojure/edn code
  rewrite-clj {:mvn/version "0.6.2"}
  ;; Customizable Clojure code formatter
  cljfmt {:mvn/version "0.6.4"}}}

```

Det kule med `rewrite-clj` er at linjeskift, mellomrom og kommentarer taes vare på (som selvstendige noder). Når du navigerer med `rewrite-clj.zip` funksjoner som `next`, `left`, `right` osv vil den skippe over linjeskift/blanke tegn/kommentarer (du kan nå disse med å bruke navigasjonsfunksjonenene i `clojure.zip`).
Det er faktisk ganske kjekt!

Mye som kunne vært sagt om `rewrite-clj`, men det får nesten bli en annen bloggpost.



Happy zipping folks.
