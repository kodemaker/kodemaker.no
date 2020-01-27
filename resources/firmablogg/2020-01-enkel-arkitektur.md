:title En enkel frontendarkitektur som funker
:published 2020-01-29
:author magnar
:tech [:frontend :design :functional-programming]

:blurb

Det er mange store stilaser å velge blant for din frontendarkitektur, men
trenger du all leamikken? Her er en enkel arkitektur som jeg har hatt mye glede
av.

:body

Det er mange store stilaser å velge blant for din frontendarkitektur, men
trenger du all leamikken? Her er en enkel arkitektur som jeg har hatt mye glede
av.

Her er de viktigste poengene:

- All data er samlet på én plass.
- Dataflyten er forutsigbar og ensrettet.
- UI-komponentene får alle dataene sine tilsendt.
- UI-komponentene er uavhenging av domene og kontekst.
- Handlinger kommuniseres fra UI-komponentene via data.
- De bevegelige delene er samlet på toppnivå i en main-funksjon.

## Kort fortalt

App-en sparkes i gang av en `main`-metode, som oppretter et sted å samle dataene.
Disse hentes, og sendes til en `prepare`-funksjon som gjør domenedata om til
UI-data. UI-dataene rendres ved hjelp av generiske komponenter.

<svg version="1.1" id="Layer_1" xmlns="http://www.w3.org/2000/svg" x="0" y="0" viewBox="230 350 400 180" xml:space="preserve"><style>.st0{fill:#fff;stroke:#000;stroke-miterlimit:10}.st1{font-family:&apos;ArialMT&apos;}.st2{font-size:10px}.st3,.st4{fill:none;stroke:#000;stroke-miterlimit:10}.st4{stroke-width:.75}</style><path class="st0" d="M375.5 404.88h-42c-5.52 0-10-4.48-10-10v-15.75c0-5.52 4.48-10 10-10h42c5.52 0 10 4.48 10 10v15.75c0 5.52-4.48 10-10 10zM519.5 418.5H626V512H519.5z"/><text transform="translate(343.663 391)" class="st1 st2">Main</text><text transform="translate(250.935 446.614)"><tspan x="0" y="0" class="st1 st2">Domene-</tspan><tspan x="10.83" y="11" class="st1 st2">data</tspan></text><ellipse class="st0" cx="270.88" cy="418.81" rx="30.75" ry="12.81"/><ellipse class="st0" cx="270.88" cy="475.19" rx="30.75" ry="12.81"/><path class="st3" d="M240.12 417.96v56.37M301.62 417.96v56.37"/><path class="st4" d="M309 450.5h59.7"/><path d="M374 450.5l-7.46 3.05 1.77-3.05-1.77-3.05z"/><text transform="translate(320.342 445.96)" class="st1 st2">prepare</text><path class="st4" d="M428.5 450.5h74.7"/><path d="M508.5 450.5l-7.46 3.05 1.77-3.05-1.77-3.05z"/><text transform="translate(442.896 444.96)"><tspan x="0" y="0" class="st1 st2">generiske</tspan><tspan x="-7.5" y="16" class="st1 st2">komponenter</tspan></text><text transform="translate(387.18 435.96)" class="st1 st2">UI-data</text><text transform="translate(564.91 435.96)" class="st1 st2">DOM</text><g><path class="st4" d="M323.5 394.5L305 409.63"/><path d="M301.5 412.5c1.15-1.96 2.34-4.68 2.7-6.82l1.26 3.58 3.26 1.94c-2.17-.07-5.07.56-7.22 1.3z"/></g><g><path class="st4" d="M404.5 441.5l-14 30M404.02 442.71l20.48 29.79"/><circle transform="rotate(-27.365 404.043 442.822)" cx="404.1" cy="442.82" r="2.82"/><path class="st4" d="M390.5 471.5l-14.43 16.56M390.5 471.5l13 15M403.5 486.5l-12 20M403.5 486.5l12 21"/></g><g><path class="st4" d="M575.5 441.5l-14 30M575.02 442.71l20.48 29.79"/><circle transform="rotate(-27.365 575.032 442.833)" cx="575.1" cy="442.82" r="2.82"/><path class="st4" d="M561.5 471.5l-14.43 16.56M561.5 471.5l13 15M574.5 486.5l-12 20M574.5 486.5l12 21"/></g><path class="st3" d="M519.5 422.59H626"/></svg>

## Dataflyt

Dataene dine kommer til klienten på ett eller annet vis. Jeg skal ikke begi meg
inn på hvordan i denne bloggposten, annet enn å si at det ikke er komponentene
som henter dem selv. Kanskje henter du dem med GraphQL, eller WebSockets, eller
noen GET-requests - så lenge det gjøres sentralt, skal jeg ikke klage.

Når du har datene, så samles de på toppnivå på en plass som er definert av
main-funksjonen. Det kan være i [en database](/datascript/), i et
[atom](https://clojure.org/reference/atoms), eller til nøds i et JS-objekt.

Uansett trenger du å vite når dataene har endret seg, slik at en oppdatering av
UI-et kan sparkes igang.

Når dette skjer kalles en `prepare`-funksjon med alle dataene, som gjør
domenedata om til UI-data. Disse UI-dataene sendes til en toppnivå komponent,
som tegner ut UIet med generiske komponenter.

Det er hele dataflyten. Når det kommer endringer til dataene, skjer alt dette om
igjen. [Virtual DOM](https://github.com/snabbdom/snabbdom)-trikset (gjort
populært av React) lar oss gjør dette uten store ytelsesproblemer.*

<small>* Ut av boksen for ClojureScript, men store JavaScript-prosjekter må kanskje ty til immutable.js</small>

## Generiske komponenter

Dette er byggeblokkene våre. De implementerer designet vårt, men kjenner ikke
til domenet. De kjenner ikke til konteksten de brukes i. De vet ikke hva slags
handlinger som utføres når knapper trykkes på.

Dette gjør komponentene særdeles gjenbrukbare. Når vi går fra en
`RegistrationButton` til en `PrimaryButton`, så kan den brukes mange steder. Man
får et eget språk for designet, fristilt fra domenespråket.

Men hva da med actions? Skal ikke `RegistrationButton` gjøre noe annet enn
`SignInButton`? Jo, men hvilke handlinger som skal utføres sendes også inn til
komponenten som data.

Enklet fortalt:

```
PrimaryButton({action: ["register-user"]})
```

Det eneste `PrimaryButton` vet er at når den trykkes på, så skal `action` puttes
på en event-bus. Denne overvåkes av main-funksjonen, som så gjennomfører handlingen.

<svg version="1.1" id="Layer_1" xmlns="http://www.w3.org/2000/svg" x="0" y="0" viewBox="230 350 400 180" xml:space="preserve"><style>.st0{fill:#fff;stroke:#000;stroke-miterlimit:10}.st1{font-family:&apos;ArialMT&apos;}.st2{font-size:10px}.st3,.st4{fill:none;stroke:#000;stroke-miterlimit:10}.st4{stroke-width:.75}.st5{font-size:7px}</style><path class="st0" d="M375.5 404.88h-42c-5.52 0-10-4.48-10-10v-15.75c0-5.52 4.48-10 10-10h42c5.52 0 10 4.48 10 10v15.75c0 5.52-4.48 10-10 10zM519.5 418.5H626V512H519.5z"/><text transform="translate(343.663 391)" class="st1 st2">Main</text><path class="st0" d="M509.5 404.88h-42c-5.52 0-10-4.48-10-10v-15.75c0-5.52 4.48-10 10-10h42c5.52 0 10 4.48 10 10v15.75c0 5.52-4.48 10-10 10z"/><text transform="translate(466.264 391)" class="st1 st2">Event bus</text><text transform="translate(250.935 446.614)"><tspan x="0" y="0" class="st1 st2">Domene-</tspan><tspan x="10.83" y="11" class="st1 st2">data</tspan></text><ellipse class="st0" cx="270.88" cy="418.81" rx="30.75" ry="12.81"/><ellipse class="st0" cx="270.88" cy="475.19" rx="30.75" ry="12.81"/><path class="st3" d="M240.12 417.96v56.37M301.62 417.96v56.37"/><path class="st4" d="M309 450.5h59.7"/><path d="M374 450.5l-7.46 3.05 1.77-3.05-1.77-3.05z"/><text transform="translate(320.342 445.96)" class="st1 st2">prepare</text><path class="st4" d="M428.5 450.5h74.7"/><path d="M508.5 450.5l-7.46 3.05 1.77-3.05-1.77-3.05z"/><text transform="translate(442.896 444.96)"><tspan x="0" y="0" class="st1 st2">generiske</tspan><tspan x="-7.5" y="16" class="st1 st2">komponenter</tspan></text><text transform="translate(387.18 435.96)" class="st1 st2">UI-data</text><text transform="translate(564.91 435.96)" class="st1 st2">DOM</text><path class="st4" d="M323.5 394.5L305 409.63"/><path d="M301.5 412.5c1.15-1.96 2.34-4.68 2.7-6.82l1.26 3.58 3.26 1.94c-2.17-.07-5.07.56-7.22 1.3z"/><g><path class="st4" d="M404.5 441.5l-14 30M404.02 442.71l20.48 29.79"/><circle transform="rotate(-27.365 404.043 442.822)" cx="404.1" cy="442.82" r="2.82"/><path class="st4" d="M390.5 471.5l-14.43 16.56M390.5 471.5l13 15M403.5 486.5l-12 20M403.5 486.5l12 21"/></g><g><path class="st4" d="M575.5 441.5l-14 30M575.02 442.71l20.48 29.79"/><circle transform="rotate(-27.365 575.032 442.833)" cx="575.1" cy="442.82" r="2.82"/><path class="st4" d="M561.5 471.5l-14.43 16.56M561.5 471.5l13 15M574.5 486.5l-12 20M574.5 486.5l12 21"/></g><path class="st3" d="M519.5 422.59H626"/><g><path class="st4" d="M555.5 418.5l-28.32-20.36"/><path d="M523.5 395.5c2.19.6 5.12 1.05 7.29.85l-3.13 2.14-1.03 3.65c-.5-2.12-1.86-4.76-3.13-6.64z"/></g><text transform="translate(540.81 403.481)" class="st1 st5">Handlinger som data</text><g><path class="st4" d="M385.5 385.5l63.47.93"/><path d="M453.5 386.5c-2.14.76-4.8 2.07-6.46 3.47l1.34-3.55-1.24-3.58c1.62 1.45 4.24 2.84 6.36 3.66z"/></g><text transform="translate(389.202 380.862)" class="st1 st5">overvåker og utfører</text></svg>

## Fra domenedata til generiske komponenter

Ettersom komponentene ikke snakker domenespråk, så trenger vi en tolk. Det er
`prepare`-funksjonen. Den tar domenedataene fra den sentrale datakilden, og gjør
om til håndsydde data for nettopp det UIet vi ser på nå.

Dataene fra `prepare` skal i så stor grad som mulig gjenspeile UI-et. Den bygger
en trestruktur som kan sendes rett ned til komponentene.

Dette gjør at selve komponent-koden kan være så godt som fri fra logikk. UI-kode
er notorisk vanskelig å teste. Her kan vi koble oss på ett hakk over, og likevel
få testet logikken.

## Til slutt

Dette er en arkitektur jeg har brukt med glede på små og store prosjekter de
siste fem årene.

Hva får man egentlig av denne arkitekturen?

 - En dataflyt som er lett å følge.
 - Gjenbrukbare komponenter som implementerer designet.
 - Reproduserbart brukergrensesnitt pga én datakilde.
 - Fri fra det evige rammeverkkjøret.
