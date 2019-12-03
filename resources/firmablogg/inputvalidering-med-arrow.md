:title Inputvalidering i Kotlin med Arrow
:author frode
:published 2019-12-04
:tech [:kotlin :arrow]

:blurb

Arrow er et nyttig lite bibliotek som er ment som en utvidelse av Kotlins standardbibliotek med fokus på funksjonell programmering. I dette innlegget skal vi se på hvordan vi kan bruke datatypen `Validated` til å gjøre inputvalidering morsommere, mer effektivt og ikke minst funksjonelt.

:body

Inputvalidering er kanskje ikke den mest spennende delen av fagfeltet vårt, men det er viktig. Det er en del av det daglige arbeidet for de aller fleste av oss enten vi lager brukergrensesnitt eller APIer i backend. Et godt opplegg for inputvalidering vil spare oss for mye tid etter hvert som ny funksjonalitet legges til og gjøre applikasjonen eller APIet vårt hyggeligere å bruke.

Vi skal se litt på hvordan vi kan gjøre inputvalidering i [Kotlin](https://kotlinlang.org/) ved hjelp av [Arrow](https://arrow-kt.io/). Arrow er ment som en utvidelse av Kotlins standardbibliotek med fokus på funksjonell programmering og henter inspirasjon fra [Haskell](https://www.haskell.org/) og [Scala](https://www.scala-lang.org/). Her finner du en rekke nyttige datatyper (som `Either`, `Option`, `Validated` og `IO`), et rikt biblitek av funksjoner og abstraksjoner som `Functor`, `Applicative` og `Monad`.

## Oppgaven

Se for deg at du skal sende inn et skjema for å registrere en bruker. Du fyller ut alle feltene etter beste evne og trykker send. En feilmelding viser at det snek seg inn et punktum for mye i epostadressen. Du retter det og sender på nytt og får beskjed om at passordet ikke er langt nok. Send på nytt: Du glemte å huke av for lest vilkår. Send på nytt... Dette er en fin måte å irritere brukerne på. Vi må altså sørge for at vi validerer alle innsendte verdier samtidig og gir tilbake alle feilmeldingene i responsen.

Som et minimum har vi følgende mål:

* Valideringsfeil må være spesifikke slik at de kan håndteres programmatisk og riktig melding kan vises til brukeren, og beskrivende slik at en utvikler vet hva som er galt.
* Validere alle innsendte verdier og samle opp eventuelle feil
* Sørge for at vi kun har gyldige verdier før vi oppretter domeneobjekter og kaller funksjoner med forretningsregler

## Validated

Validated er en datatype som enten representerer en gyldig verdi (etter et eller annet kriterium for gyldighet) eller en valideringsfeil. I Arrow ser det omtrent sånn ut:

```kotlin
sealed class Validated<out E, out A> {
  data class Valid<out A>(val a: A) : Validated<Nothing, A>()
  data class Invalid<out E>(val e: E) : Validated<E, Nothing>()
}
```

En enkel generisk type med to typeparametre: E for valideringsfeil og A for den validerte typen. Validated består av kun to mulige representasjoner, nemlig `Valid` med tilhørende ønsket verdi og `Invalid` med en valideringsfeil.

Dette minner mistenkelig om `Either`. Bare med litt mer spesifikke navn. Kan vi ikke bare bruke `Either` sin `Left` for valideringsfeil og `Right` for ønsket verdi? Jo det kan vi godt gjøre. Men `Either` er en `Monad` og en `Monad` har litt mer kraft enn vi egentlig trenger for å gjøre inputvalidering. `Validated` er nemlig "bare" en `Applicative Functor`. Men hva betyr det da? Ingen av oss er tjent med at dette [sklir helt ut](https://arrow-kt.io/docs/patterns/monads/#the-fallacy-of-monad-tutorials) i en [monad tutorial](http://adit.io/posts/2013-04-17-functors,_applicatives,_and_monads_in_pictures.html), men jeg kan i det minste komme med to enkle huskeregler:

* **Monad** egner seg til sekvensielle operasjoner der en operasjon avhenger av resultatet fra den forrige
* **Applicative Functor** egner seg til operasjoner som er uavhengige av hverandre

Sett i lys av disse huskereglene og vårt initielle mål om å gi brukeren alle valideringsfeilene i samme respons, så høres jo `Validated` midt i blinken ut.

## Eksempeldomene

Dagens grovt forenklede eksempeldomene er registrering av lag og spillere i et system for idrettslag. Vi definerer følgende domenetyper:

````kotlin
data class Player(val name: String, val birthYear: Int)
data class Team(val name: String, val players: List<Player>)
````

## Valideringsfeil

Vi definerer en enkel modell for valideringsfeil som er fleksibel nok til å ta høyde for ulike typer valideringsfeil med forskjellig parametre.

```kotlin
sealed class ValidationError(val errorMessage: String)

object InvalidName : ValidationError("Name is required and cannot contain numbers or special characters")
object InvalidBirthYear : ValidationError("Birth year is required and must have format YYYY")
data class NotEnoughPlayers(val minPlayers: Int): ValidationError("A team must have at least $minPlayers players")
```

## ValidatedNel

Verdiene vi skal validere kommer inn til APIet på et eller annet format. F.eks. json eller xml. Vi må ta høyde for at de kan være både null og tomme verdier. Vi er klare til å definere noen enkle valideringsfunksjoner:

```kotlin
fun validateName(name: String?): ValidatedNel<InvalidName, String> =
    when {
      name == null || !name.matches(nameRegex) -> InvalidName.invalidNel()
      else -> name.validNel()
    }

fun validateBirthYear(birthYear: String?): ValidatedNel<InvalidBirthYear, Int> =
    when {
      birthYear == null || !birthYear.matches(birthYearRegex) -> InvalidBirthYear.invalidNel()
      else -> birthYear.toInt().validNel()
    }
```

Vent nå litt. Hva er `ValidatedNel`? Skulle ikke vi bruke `Validated`? For å samle opp valideringsfeil ønsker vi bruke de akkumulative egenskapene til `Validated`. For å få til det trenger vi en container som kan holde på flere enn én valideringsfeil. Til dette bruker vi Arrows `NonEmptyList` som fungerer som en vanlig liste bortsett fra at den alltid har minst ett element. `NonEmptyList` fortkortes ofte bare `Nel` og er så ofte brukt sammen med `Validated` at det finnes et `typealias ValidatedNel<E, A> = Validated<Nel<E>, A>` med tilhørende extension-funksjoner `.validNel()` og `.invalidNel()` for å pakke en gyldig verdi inn i en `ValidNel` og en valideringsfeil i en `InvalidNel`

Med dette på plass er det på tide å kombinere validering av enkeltverdier til en `Player`:

```kotlin
val VA = ValidatedNel.applicative(Nel.semigroup<ValidationError>())

fun validatePlayer(name: String?, birthYear: String?): ValidatedNel<ValidationError, Player> =
    VA.map(
        validateName(name),
        validateBirthYear(birthYear)
    ) { Player(it.a, it.b) }.fix()
```

Hvis vi først ser bort fra den noe mystiske definisjonen av `VA` så ser dette ganske greit ut. Vi kaller funksjonen `.map()` med resultatet fra hver av valideringsfunksjonene og hvis begge er gyldige (instans av `ValidNel`) så kaller vi lambdaen `{ Player(it.a, it.b) }` med et `Tuple2` med de to gyldige verdiene for `name` og `birthYear` så vi kan instansiere en `Player`. Skulle en eller begge funksjonene returnere `InvalidNel` blir også resultatet av `validatePlayer` `InvalidNel` med alle valideringsfeilene akkumulert. Poenget her er at ved å bruke en ferdig implementasjon av abstraksjonen `Applicative` får vi oppsamling av valideringsfeil uten å implementere det selv.

I bruk vil dette se ut som følger:

```kotlin
val validPlayer = validatePlayer(
    name = "Frida Fotballspiller",
    birthYear = "2011"
)
// res1 = Valid(a=Player(name=Frida Fotballspiller, birthYear=2011))

val invalidPlayer = validatePlayer(
    name = "H4xe H4x0r",
    birthYear = "79"
)
// res2 = Invalid(e=NonEmptyList(all=[
//    InvalidName(errorMessage=Name is required and cannot contain numbers or special characters),
//    InvalidBirthYear(errorMessage=Birth year is required and must have format YYYY)
//  ]))
```

## Type classes

Referansen `val VA = ValidatedNel.applicative(Nel.semigroup<ValidationError>())` ovenfor er et eksempel på en instans av en **type class**. En type class er som et interface. Forskjellen fra vanlige interface i Kotlin er at implementasjonen ikke er en del av klassedefinisjonen til den typen som implementerer interfacet. Den ligger separat og kalles en instans av type-classen. `Applicative` er en type class som igjen er en del av et større type-class-hierarki i Arrow med en rekke hendige funksjoner. For hver type som implementerer `Applicative` finnes det en konkret implementasjon - en instans. VA ovenfor er simpelthen en referanse til Applicative-instansen til `ValidatedNel`. Denne trenger ytterligere en instans av type class-en `Semigroup` til den containeren som skal brukes til å akkumulere valideringsfeil - nemlig `NonEmptyList`. `Semigroup` gir et standard interface for hvordan to verdier kan slås sammen slik at Applicative kan brukes med vilkårlige typer som kan akkumuleres.

Til syvende og sist kan man se på `val VA = ValidatedNel.applicative(Nel.semigroup<ValidationError>())` som en vanlig import av et sett støttefunsjoner skreddersydd til `ValidatedNel`. Den noe kronglete syntaksen skyldes at Kotlin ikke har native støtte for type classes slik som Haskell og til dels Scala (via implicits) har.

## Sekvensiell validering

Det har vært mye snakk om at alle verdier skal valideres uavhengig av hverandre, men hva om en validering faktisk avhenger av svaret på en annen? Vi kan for eksempel se for oss at det ikke gir mening å validere antall spillere på et lag før vi vet at alle spillerene er gyldige:

```kotlin
val VA = ValidatedNel.applicative(Nel.semigroup<ValidationError>())

fun validateTeam(minPlayers: Int, name: String?, players: List<Player>): ValidatedNel<ValidationError, Team> =
    VA.map(
        validateTeamName(name),
        if (players.size < minPlayers) NotEnoughPlayers(minPlayers).invalidNel() else players.validNel()
    ) { Team(it.a, it.b) }.fix()
```

Arrow har et noe rikere typehierarki internt og i enkelte situasjoner må man bruke funksjonen `fix()` for å konvertere tilbake til vanlige typer. Dette er kun en cast for å hjelpe Kotlins typeinferens.

Vi validerer en liste med spillere på følgende måte:

```kotlin
val players = listOf(
    validatePlayer("Frida Fotballspiller", "2011"),
    validatePlayer("Kjersti Keeper", "2011"),
    validatePlayer("Tone Toppscorer", "2011")
)
// players: List<ValidatedNel<ValidationError, Player> = [
//      Valid(a=Player(name=Frida Fotballspiller, birthYear=2011)),
//      Valid(a=Player(name=Kjersti Keeper, birthYear=2011)),
//      Valid(a=Player(name=Tone Toppscorer, birthYear=2011))
//    ]
```

Her har vi støtt på et lite problem. `validateTeam` tar inn en liste med spillere, men alt vi har er en liste med muligens gyldige spillere eller valideringsfeil. Vi må rett og slett vrenge litt. Først ønsker vi å samle opp alle spillerne i en liste hvis alle er gyldige. Hvis minst én er ugyldig ønsker vi en liste med valideringsfeil. Igjen får vi dette fra Arrows Applicative-instans:

```kotlin
val validatedPlayers = players.sequence(VA).fix()
// validatedPlayers: ValidatedNel<ValidationError, List<Player>> = Valid(a=ListK(list=[
//      Player(name=Frida Fotballspiller, birthYear=2011),
//      Player(name=Kjersti Keeper, birthYear=2011),
//      Player(name=Tone Toppscorer, birthYear=2011)
//    ]))
```

Suksess! Men hvis vi nå gjør `validatedPlayers.map { validateTeam(2, "Gråvika J2011", it.fix()) }` så blir resultatet `ValidatedNel<ValidationError, ValidatedNel<ValidationError, Team>>` og det er ikke det vi ønsker. Ikke noe problem tenker den våkne leser, jeg bruker bare `flatMap()` i stedet. Men det er her `Applicative` kommer til kort. Den har ingen `flatMap()` fordi den ikke er en `Monad`. Men fortvil ikke. Husker du vi snakket om `Either` innledningsvis? Siden den er så lik `Validated` er det trivielt å konvertere frem og tilbake:

```kotlin
val validTeam = validatedPlayers.toEither().flatMap { players ->
  validateTeam(2, "Gråvika J2011", players.fix()).toEither()
}.toValidated()
// validTeam: ValidatedNel<ValidationError, Team> = Valid(a=Team(name=Gråvika J2011, players=ListK(list=[
//      Player(name=Frida Fotballspiller, birthYear=2011),
//      Player(name=Kjersti Keeper, birthYear=2011),
//      Player(name=Tone Toppscorer, birthYear=2011)
// ])))
```

`validatedTeam` vil nå enten innholde et gyldig `Team` eller en liste av valideringsfeil fra valideringen av spillere **eller** valideringsfeilene fra valideringen av selve laget.

## En start

Med Arrows `Validated` og en enkel struktur for valideringsfeil har vi et fint utgangspunkt for å lage et rikt sett av domenespesifikke valideringsfunksjoner. Mesteparten av mekanikken er implementert for oss og vi kan konsentrere oss om valideringsreglene.

Vi har også, med validering som eksempel, skrapet i overflaten på hva Arrow med sin verktøykasse for funksjonell programmering kan tilby. Noen begreper kan virke fremmede, men det er bare patterns som går igjen så ofte i funksjonell programmering at man har laget generiske abstraksjoner for dem. Med Arrow slipper man å implementere disse selv og man får en struktur på koden som andre som kan funksjonell programmering vil kjenne seg igjen i.
