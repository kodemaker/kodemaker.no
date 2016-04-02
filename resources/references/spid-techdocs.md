--------------------------------------------------------------------------------
:page-title Schibsted Payment ID
:type reference
:logo /logos/spid.svg
:img /references/claes-bergsten.jpg
:name Claes Bergsten
:phone +47 916 68 984
:title Tech Lead Payment, Schibsted Payment ID
:body

Sammen med Kodemaker ønsket vi å øke kvaliteten på vår tekniske
utviklerdokumentasjon. Vi ønsket et penere, mer responsivt uttrykk og hvis mulig
å automatisere deler av prosessen. Prosjektet hadde ingen føringer på teknologi
eller verktøy og Kodemaker var fra første dag en proaktiv partner som hjalp
til å forme løsningen. Vi opplevde konsulentene som svært faglig dyktige og de
ble fort en del av gjengen her. De hadde ingen problemer med å sette seg inn i
våre problemstillinger og vi endte med en løsning som leverte mer enn vi i
utgangpunktet hadde ambisjoner om å få til.

--------------------------------------------------------------------------------
:type illustrated-column
:title Teknisk dokumentasjon på kritisk sti
:body

Schibsted Payment ID (SPiD) håndterer innlogging og betaling for FINN.no, VG,
Aftenposten, Dine Penger og Aftonbladet. I tillegg til disse store selskapene,
ønsker SPiD å kunne tilby sine tjenester også til mindre bedrifter.

SPiD så at nye integrasjoner var vanskelige for nye kunder å gjennomføre uten å
lene seg tungt på SPiD sine utviklingsteam. Den tekniske dokumentasjonen var
utdatert og mangelfull. Resultatet var at de ikke kunne rulle ut til så mange
kunder som de hadde ønsket. Det var i denne sammenheng at Kodemaker ble
kontaktet.

--------------------------------------------------------------------------------
:type reference-meta
:title SPiD Techdocs
:body

To Kodemakere i team leverte i 2014 et dedikert nettsted for teknisk dokumentasjon.
Utviklingen foregikk hos SPiD over en 6 måneders periode.

:team-size 2
:factoid-1 2 Kodemakere
:factoid-2 950 timer / 02.2014-08.2014

--------------------------------------------------------------------------------
:type illustrated-column
:body

Techdocs-siten består av:

- Praktiske guides med mange kode-eksempler.
- Komplette, fungerende eksempler som kan sjekkes ut og kjøres selvstendig.
- Utfyllende API-dokumentasjon.

> "Vi er jo begge utviklere, så vi lagde den dokumentasjonen vi selv skulle ønske vi hadde."
>
> -- <cite>Christian</cite>

--------------------------------------------------------------------------------
:type illustrated-column
:title Levende dokumentasjon
:illustration /illustrations/references/spid-tech-docs.png
:illustration-url http://techdocs.spid.no/
:body

Vi tok mange grep for å sørge for at dokumentasjonen denne gangen skulle holde
seg komplett og oppdatert.

- Kode-eksemplene i guidene hentes direkte fra de kjørende eksemplene. Dersom de
  kjørende eksemplene oppdateres i forbindelse med en endring, så vil
  oppdateringen automatisk også gjenspeiles i guiden.

- Eksempler på respons fra serveren for hvert endepunkt hentes inn i
  dokumentasjonen ved å utføre de faktiske kallene til en staging-server.
  Resultatet formateres og vaskes for sensitive data.

- Det blir generert kode-eksempler for bruk av hvert API-endepunkt, basert på
  informasjonen som hentes ut av applikasjonen.

--------------------------------------------------------------------------------
:type ginormous-aside
:aside 103
:body

Over hundre endepunkter i APIet måtte dokumenteres. En stor jobb å
skrive -- men særlig å vedlikeholde. Med Kodemakers tilnærming blir sidene
generert ut ifra metadata fra selve applikasjonen. Hvis endepunkter i APIet
endres eller legges til, speiles det automatisk i dokumentasjonen.

--------------------------------------------------------------------------------
:type illustrated-column
:body

> "Når SPiD skulle legge til et SDK for Node.JS, var det snakk om en times
>  arbeid å legge til hundrevis av eksempler på bruk av det nye SDKet."
>
> -- <cite>Magnar</cite>

--------------------------------------------------------------------------------
:type illustrated-column
:title Et case for funksjonell programmering
:body

Nettstedet ble utviklet på nytt fra grunnen av. Ettersom vi skulle massere data
hentet inn fra en rekke kilder valgte vi en tilnærming med funksjonell
programmering med [Clojure](/clojure/). Vi skulle generere en relativt stabil
innholdsside, så det ga mening å lage statiske sider med [Stasis](/stasis/) og
[Optimus](/optimus/) - teknologier vi kjente godt fra å lage Kodemakers egne
nettsider.

--------------------------------------------------------------------------------
:type grid
:content

/javascript/                       /photos/tech/js.svg
/responsive-design/                /photos/tech/rwd.jpg 2x
/ansible/                          /photos/tech/ansible-red.svg
/git/                              /photos/tech/git-gray.svg
/oocss/                            /photos/tech/oocss-wide.png 2x
/nodejs/                           /photos/tech/nodejs-simple.svg
/optimus/                          /photos/tech/optimus.png
/stasis/                           /photos/tech/stasis-wide.svg 2x

--------------------------------------------------------------------------------
:type illustrated-column
:body

Siden vi fikk lage nettstedet fra bunn av, var det naturlig å gjøre det med
[OOCSS](/oocss/). Da kostet det oss lite å også gjøre sidene
[responsive](/responsive-design/), slik at de fungerer godt både på nettbrett og
mobil.

Vi valgte også en løsning der dokumentasjonen ikke ligger i en database på en
server, men vedlikeholdes i [Git](/git/). Utviklerne har nå muligheten til å
løpende dokumentere det de lager -- uten at det lager støy i den online
dokumentasjonen. Kodebasen og dokumentasjonen kan følge samme branching-strategi
og livssyklus.

--------------------------------------------------------------------------------
:type participants
:title Kodemakere hos Schibsted
:content

christian

Christian og Magnar jobbet mye sammen, og hadde begge fingerene i det meste. Når
Christian jobbet alene brøytet han seg gjennom alle endepunktene i APIet, og
beskrev alt som var - inkludert et hierarki av datatyper og datastrukturer i
bruk. Han lagde systemet for å hente ut faktiske resultater fra staging-server
til bruk som eksempler.

magnar

Når Magnar ikke jobbet sammen med Christian, gikk det mye i å lage
infrastrukturen rundt oppbygging av sidene. Målet var å gjøre det så lett som
mulig å vedlikeholde dokumentasjonen. Han lagde også den responsive designen, og
importrutinen for endepunkter.

--------------------------------------------------------------------------------
:type illustrated-column
:title En ny løsning på et gammelt problem
:body

Nye endringer i den tekniske dokumentasjonen rulles ut kontinuerlig, slik at
hver commit til techdocs-repoet gjenspeiles på nettstedet etter få minutter.

Det er et gammelt problem: Hvordan holder man dokumentasjonen oppdatert? Med
Kodemakers hjelp har SPiD fått en moderne løsning på problemet.

--------------------------------------------------------------------------------
