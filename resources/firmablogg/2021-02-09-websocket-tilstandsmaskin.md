:title Lage ditt eget websocket-bibliotek som en tilstandsmaskin?
:author magnus
:tech [:typescript :frontend :funksjonell-programmering]
:published 2021-02-09

:blurb

I jula som var satt jeg og nerdet litt innimellom all julekosen. Jeg prøvde å finne et lite bibliotek for enkel håndtering av websockets i en nettleserapplikasjon.
Nå følger det jo med en standard WebSocket implementasjon i alle nettlesere, men jeg ville også at den skulle ha støtte for automatisk gjenoppkobling og pulskontroll mot server.
Etter å ha søkt litt på npm som seg hør og bør, fant jeg ikke noen som helt fristet å dra inn. Kanskje jeg skal lage et lite bibliotek selv da?
Hva om jeg implementer det ved å basere meg på en [Finite State Machine](https://en.wikipedia.org/wiki/Finite-state_machine) i bunn?
Det hadde vært digg om biblioteket hadde en funksjonell kjerne og at sideeffekter håndteres separat. Her er det bare å brette opp armene å se hva vi kan få til.


:body
La oss forestille oss at du har bestemt deg for å benytte deg av WebSockets i din nye fete web applikasjon. Du begynner kanskje først med å bare bruke
det innebygde WebSocket APIet som følger med i nettleseren. Du finner etterhvert ut at det hadde vært kjekt med automatisk gjenoppkobling og en eller annen form
for keep-alive mellom klient og server. La oss også anta at du har valg TypeScript til frontenden din av en eller annen grunn. [SockJS](https://github.com/sockjs) som mange bruker virker
litt vel omfattende og passer kanskje ikke med din valgte teknologistakk på serveren.  Ditt første instinkt er jo da kanskje
å sjekke på [npm](https://www.npmjs.com/) om det finnes et bibliotek som passer. Du finner selvfølgelig mye greier. Men ingen av de du finner virker overbevisende nok.
Det er kanskje ikke rasjonelt, men du bestemmer deg for å lage det selv. Da har du full kontroll, kan få det akkurat som du selv vil og du risikerer å lære endel underveis.

Funksjonell programmering er jo noe mange snakker om. Passer det til noe som er så fullt av [sideeffekter](https://en.wikipedia.org/wiki/Side_effect_(computer_science)) som håndtering av WebSockets?
La oss prøve, så kan du jo vurdere etterpå om du syntes det var verdt innsatsen.

## Oppsett
- `npx tsdx create ts-ws-diy`
- `basic`

Voila så har du et TypeScript prosjekt oppe, med masse snurrepipperier, ferdigkonfigurert for testing, bygg, linting osv.
Personlig er jeg ikke så veldig glad i sånne boilerplate generatorer med masse skjult config, men det lar oss komme kjapt i gang.

Siden vi skal ha det artig med funksjonelle typer så installerer vi også det kjekke biblioteket [unionize](https://github.com/pelotom/unionize)

```bash
npm i -S unionize
```

## En myk start
![Tilstandsdiagram](/images/blogg/ws_simple_state.png)
Vi begynner med å implementere tilstandsmaskinen i diagrammet over. Det er ikke det endelige målet, men det er greit å begynne litt mykt
når vi skal prøve oss frem.

Lag en ny typescript fil `src/statemachine.ts`

```typescript
import unionize, { ofType } from "unionize";

// Definerer de forskjellige tilstandene vi har
export const States = unionize({
  INITIAL: ofType<{}>(),
  CONNECTING: ofType<{}>(),
  OPENED: ofType<{}>(),
  CLOSED: ofType<{}>(),
});

// Lager en typescript type av tilstandsdefinisjonen vår over
export type State = typeof States._Union;

// Dette er meldingene/hendelsene som kan forårsake en tilstandstransisjon
export const Events = unionize({
  ON_CONNECT: {},
  ON_OPEN: {},
  ON_CLOSE: {}
});
export type Event = typeof Events._Union;
```

Dette var jo sikkert flott, men det blir jo ikke rare tilstandsmaskinen dersom vi ikke har en funksjon for å foreta en transisjon
fra en tilstand til en neste. Signaturen på denne funksjonen definerer vi slik:
```typescript
const TransitionFn = (evt: Event, state: State) => State;
```

En artig liten kuriositet er at denne funksjonen minner ganske mye på [update](https://guide.elm-lang.org/architecture/buttons.html#update) funksjonen i Elm:
```elm
update : Msg -> Model -> Model
update msg model =
```
En sammenligning som kanskje er mer nærliggende dersom man kommer fra JavaScript/TypeScript miljøet er [reducers](https://redux.js.org/tutorials/fundamentals/part-3-state-actions-reducers#writing-reducers) i redux
```typescript
(state: State, action: Action) => State
```

Fellestrekket for alle 3 er at de er rene funksjoner ([pure functions](https://en.wikipedia.org/wiki/Pure_function)).
De er (/skal være) funksjoner som ikke har sideeffekter, inputparametere skal/kan ikke endres av funksjonen og returverdien er utelukkende en funksjon av inputparameterene.
Slike funksjoner er enkle å teste og lettere å forstå enn funksjoner som muterer eller har andre skumle sideeffekter.
Ok, det får være nok salgspitch om funksjonelle prinsipper. La oss lage transisjonsfunksjonen.

```typescript
export const transition = (evt: Event, state: State): State => States.match(state, {
  INITIAL: () => Events.match(evt, {
    ON_CONNECT: () => States.CONNECTING(),
    default: () => state
  }),

  CONNECTING: () => Events.match(evt, {
    ON_OPEN: () => States.OPENED(),
    ON_CLOSE: () => States.CLOSED(),
    default: () => state
  }),

  OPENED: () => Events.match(evt, {
    ON_CLOSE: () => States.CLOSED(),
    default: () => state
  }),

  CLOSED: () => state
});
```

`Unionize` legger på en hendig hjelpefunksjon `match` som lar oss skrive `switch` ekvivalent kode på en mer elegant måte.
Dersom man lar vær å bruke `default` vil typescript kompilatoren også sørge for å si fra dersom du ikke har dekket alle tilfellene.
I koden over så er det bare de transisjonen som er gyldige som fører til tilstandsendring, alle andre permutasjoner vil returnere tilstanden uendret.
Det er ikke gitt at du ønsker å ignorere ugyldige kombinasjoner på denne måten i din implementasjon.


### Sideeffekter
Til nå har ikke transisjonsfunksjonen vår tatt innover seg at vi ønsker jo at det skal skje noe som resultat av at tilstanden endrer seg.
Når man skifter fra `INITIAL` til `CONNECTING` så ønsker vi jo at "noen" skal sørge for å faktisk initiere en websocket-forbindelse.
Det å starte en websocket-forbindelse er en sideeffekt, så vi kan jo ikke gjøre det i transisjonsfunksjonen så hvordan får vi gitt beskjed?
En (ganske lur) måte å gjøre det på er jo å returnere bestillinger på sideeffekter vi ønsker utført fra transisjonsfunksjonen.
For at transisjonsfunksjonen fortsatt skal være ren, så kan vi returnere disse bestillingene som data.

```typescript
export const Effects = unionize( {
  CONNECT_WS: {},
});
export type Effect = typeof Effects._Union;
```
Vi definerer en ny datatype for å beskrive de forskjellige effektene vi ser for oss å bestille.
Enn så lenge trenger vi bare å bestille opprettelse av en websocket-tilkobling, men det blir flere etterhvert!

Det neste vi må gjøre er å endre litt på transisjonsfunksjonen vår.
```typescript
export const transition = (evt: Event, state: State): [State, Effect[]] => States.match(state, {
  INITIAL: () => Events.match(evt, {
    ON_CONNECT: () => [States.CONNECTING(), [Effects.CONNECT_WS()]],
    default: () => [state, []]
  }),

  CONNECTING: () => Events.match(evt, {
    ON_OPEN: () => [States.OPENED(), []],
    ON_CLOSE: () => [States.CLOSED(), []],
    default: () => [state, []]
  }),

  OPENED: () => Events.match(evt, {
    ON_CLOSE: () => [States.CLOSED(), []],
    default: () => [state, []]
  }),

  CLOSED: () => [state, []]
});
```

Vi har endret signaturen på funksjonen til å returnere en tuple av `State` og en liste med `Effect`.
Siden vi endret på signaturen måtte vi også endre på alle permutasjonene. Den eneste som returnerer en faktisk
effekt-bestilling så langt er `ON_CONNECT`. Nå vet kallende funksjon at det ønskes utført en sideeffekt som følge av
denne transisjonen. Det som er najs, er at funksjonen fortsatt er sideeffektfri og lett å teste.

> Man kan jo spørre seg; Har vi egentlig oppnådd noe annet enn å modellere noe som ligner på [readyState](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket/readyState) som vi allerede har på klassen `WebSocket` i nettleseren?

Nja nei egentlig ikke, men vi har laget fundamentet for å modellere gjenoppkobling og pulskontroll.

## Gjenoppkobling og pulskontroll

![Tilstandsdiagram](/images/blogg/ws_advanced_state.png)
Oisann, det ble jo litt mer komplisert. Den nye tilstanden `RECONNECTING` var ikke så overraskende, men noen har tatt seg kreative friheter
i modelleringen av `OPENED` tilstanden. Det blir forhåpentligvis klarere når vi kommer til kapitlet om pulskontroll.



### Gjenoppkobling
Det kan være mange grunner til at en websocket kan miste forbindelsen sin til server. Kanskje mistet klienten nettforbindelse,
eller kanskje rullet man ut en ny versjon av applikasjonen på serveren(e). Det er digg om vi kunne automatisert det å forsøke
gjenoppkobling på en generisk måte. Vi gjør et forsøk på å legge til rette for det.


#### Tilstander
```typescript
export type Context = {
  reconnectAttempt: number,
}

export const States = unionize({
  INITIAL: ofType<Context>(),
  CONNECTING: ofType<Context>(),
  OPENED: ofType<Context>(),
  CLOSED: ofType<Context>(),
  RECONNECTING: ofType<Context>(),
});
```

Vi ønsker å holde orden på hvor mange ganger man har forsøkt å gjenoppkoble slik at vi kan implementere f.eks en eksponensiell "backoff".
For å ta vare på denne på tvers av alle tilstandene, lager vi en `Context` type som holder på antall forsøk og så beriker vi alle tilstandene
med denne typen. Vi har også lagt til en ny tilstand `RECONNECTING` for å representere tilstanden hvor vi venter før vi prøver å koble opp på nytt igjen.

#### Hendelser
```typescript
export const Events = unionize({
  ON_CONNECT: {},
  ON_OPEN: {},
  ON_CLOSE: {},
  ON_RECONNECT: {}, // ny
});
```
Vi legger til en ny hendelse for å representere overgangen fra tilstand `CLOSED` til tilstand `RECONNECTING`.


#### Effekter
```typescript
// Tar her høyde for at vi kan komme til å ønske flere forskjellige bestilte timeouts
// Akkurat nå støtter vi bare en timeout for å vente før vi gjør en ny oppkobling
export type TimeoutKey = "connect";

export const Effects = unionize({
  CONNECT_WS: {},
  SCHEDULE_TIMEOUT: ofType<{
    key: TimeoutKey;       // En identifikator for timeout som effekthåndteringen kan bruke for å holde orden på timeout effekter
    timeoutMillis: number; // Hvor lenge ønsker vi å vente før en hendelse skal effektueres
    onTimeout: Event;      // Hvilken hendelse vi ønsker effektuert
  }>()
});
```

Vi ser for oss at før man forsøker å koble opp på nytt igjen, så ønsker vi å kunne vente et gitt antall millisekunder før vi foretar et nytt oppkoblingsforsøk.
Siden vi ser for oss at timeouts er noe vi kan ha bruk for i andre sammenhenger også (f.eks puls), definerer vi timeout effekten litt mer generisk her.

#### Transisjoner
```typescript
export const transition = (evt: Event, state: State): [State, Effect[]] => States.match(state, {
  INITIAL: () => Events.match(evt, {
    ON_CONNECT: () => [States.CONNECTING(state), [Effects.CONNECT_WS()]],
    default: () => [state, []]
  }),

  CONNECTING: () => Events.match(evt, {
    // Endring: Dersom websocket er åpnet nullstiller vi antall gjenoppkoblingsforsøk
    ON_OPEN: () => [States.OPENED({...state, reconnectAttempt: 0}),[]],
    ON_CLOSE: () => [States.CLOSED(state), []],
    default: () => [state, []]
  }),

  OPENED: () => Events.match(evt, {
    ON_CLOSE: () => [States.CLOSED(state), []],
    default: () => [state, []]
  }),

  CLOSED: () => Events.match(evt, {
    // Ny: Vi bestiller en timeout effekt her, når den utløper så utløses hendelsen ON_CONNECT
    ON_RECONNECT: () => [
      States.RECONNECTING({...state, reconnectAttempt: state.reconnectAttempt + 1}),
      [Effects.SCHEDULE_TIMEOUT({
        key: "connect",
        timeoutMillis: 1000 * state.reconnectAttempt, // lineær backoff enn så lenge
        onTimeout: Events.ON_CONNECT()
      })]
    ],
    default: () => [state, []]
  }),

  // Ny: Vi har lagt til en ny tilstand og må håndtere at det skal være lov å gå fra denne
  // tilstanden til CONNECTING
  RECONNECTING: () => Events.match(evt, {
    ON_CONNECT: () => [States.CONNECTING(state), [Effects.CONNECT_WS()]],
    default: () => [state, []]
  })
});
```

Med disse endringene så har vi fått på plass byggestenene for en tilstandsmaskin som støtter
automatisk gjenoppkobling. Vi skulle gjerne forbedret algoritmen for hvor lenge man skal vente før vært gjenoppkoblingsforsøk, men det får vi komme tilbake til litt senere.

### Pulskontroll
Det er veldig kjekt for klienten å vite at serveren er i live og tilsvarende er det kjekt for serveren å vite
at klienten er i live. I mange skymiljøer er det ofte automatisk nedkobling dersom det ikke er aktivitet mellom klient og server. Det kan være en smule irriterende å håndtere.

Støtte for ping/pong frames er jo en del av websocket standarden, men det må da initieres fra server og du er avhengig av at serveren din
støtter å lage "ping frames". Dersom du kan det og du er trygg på at alle nettlesere du trenger å støtte har innebygd automatisk pong svar på ping frames, så kan du skippe denne delen.

> Hva med tcp keepalive: [Gammel stackoverflow](https://stackoverflow.com/questions/23238319/websockets-ping-pong-why-not-tcp-keepalive)

Ok vi er skeptiske og kjører på med vår egen ping/pong implementasjon som initieres fra klienten.


#### Tilstander
```typescript
export type Context = {
  reconnectAttempt: number,
  pingTimeoutMillis: number,
  pongTimeoutMillis: number
}
```
Vi er litt late og lar være å modellere ping og pong som egne tilstander. Man kunne kanskje argumentert for at
at ping/pong håndtering burde vært modellert som en sub-tilstandsmaskin, men det dropper vi i denne omgang.
Vi legger til timeoutverdier for ping og pong i `Context` slik at det skal bli konsistent med håndtering av timeouts for gjenoppkobling.
(Vi kunne selvfølgelig latt effekthåndteringen få inn disse verdiene, eller vi kunne hardkodet dem i transisjonsfunksjonen).

#### Hendelser
```typescript
export const Events = unionize({
  ON_CONNECT: {},
  ON_OPEN: {},
  ON_CLOSE: {},
  ON_RECONNECT: {},
  ON_HEARTBEAT: {},    // Utløses når vi har fått et pong svar eller en hvilken som helst melding fra serveren, etter vi har sendt en ping melding
  ON_PING_TIMEOUT: {}, // Utløses når timeout for å vente på å sende en ping melding har utløpt
  ON_PONG_TIMEOUT: {}, // Utløses når maks tid vi ønsker vente på svar på en utestående ping har utløpt
});
```


#### Effekter
```typescript

// Legger til ping og pong som nøkler
export type TimeoutKey = "connect" | "ping" | "pong";

export const Effects = unionize({
  CONNECT_WS: {},
  SCHEDULE_TIMEOUT: ofType<{
    key: TimeoutKey;
    timeoutMillis: number;
    onTimeout: Event;
  }>(),
  // Effekt for å avbryte en tidligere bestilt timeout-effekt
  CLEAR_TIMEOUT: ofType<{ key: TimeoutKey }>(),
  // Effekt for å sende en ping melding til serveren
  SEND_PING: ofType<{}>(),
  // Effekt for å lukke en åpen websocket forbindelse
  CLOSE_WS: {},
});
```

#### Transisjoner
```typescript
export const transition = (evt: Event, state: State): [State, Effect[]] => States.match(state, {
  INITIAL: () => Events.match(evt, {
    ON_CONNECT: () => [States.CONNECTING(state), [Effects.CONNECT_WS()]],
    default: () => [state, []]
  }),

  CONNECTING: () => Events.match(evt, {
    ON_OPEN: () => [
      States.OPENED({...state, reconnectAttempt: 0}),
      // Vi legger til en timeout-effekt for å initiere pulskontroll
      [Effects.SCHEDULE_TIMEOUT({
        key: "ping",
        timeoutMillis: state.pingTimeoutMillis,
        onTimeout: Events.ON_PING_TIMEOUT(),
      })]
    ],
    ON_CLOSE: () => [States.CLOSED(state), []],
    default: () => [state, []]
  }),

  OPENED: () => Events.match(evt, {
    // Vi sender en ping melding til server etter utløpt ping timeout
    // I tillegg ber vi om en timeout effekt for å vente på en pong i retur
    ON_PING_TIMEOUT: () => [state, [
      Effects.SEND_PING(),
      Effects.SCHEDULE_TIMEOUT({
        key: "pong",
        timeoutMillis: state.pongTimeoutMillis,
        onTimeout: Events.ON_PONG_TIMEOUT(),
      })
    ]],
    // Dersom vi ikke har fått svar fra server i tide, ber vi om å lukke underliggende websocket
    ON_PONG_TIMEOUT: () => [state, [Effects.CLOSE_WS()]],
    // Ved vellykket pulssjekk, nullstiller vi pong og ber om en ny ping timeout
    ON_HEARTBEAT: () => [
      state,
      [
        Effects.CLEAR_TIMEOUT({ key: "pong" }),
        Effects.SCHEDULE_TIMEOUT({
          key: "ping",
          timeoutMillis: state.pingTimeoutMillis,
          onTimeout: Events.ON_PING_TIMEOUT(),
        })
      ]
    ],
    ON_CLOSE: () => [
      States.CLOSED(state),
      // Ved lukking av en åpen forbindelse sørger vi for å rydde opp timeouts
      [
        Effects.CLEAR_TIMEOUT({ key: "ping" }),
        Effects.CLEAR_TIMEOUT({ key: "pong" })
      ]
    ],
    default: () => [state, []]
  }),

  CLOSED: () => Events.match(evt, {
    ON_RECONNECT: () => [
      States.RECONNECTING({...state, reconnectAttempt: state.reconnectAttempt + 1}),
      [Effects.SCHEDULE_TIMEOUT({
        key: "connect",
        timeoutMillis: 1000 * (state.reconnectAttempt + 1),
        onTimeout: Events.ON_CONNECT()
      })]
    ],
    default: () => [state, []]
  }),

  RECONNECTING: () => Events.match(evt, {
    ON_CONNECT: () => [
      States.CONNECTING(state), [Effects.CONNECT_WS()]
    ],
    default: () => [state, []]
  })
});
```

## Testing
Vi burde kanskje ha begynt med testing en god del tidligere, men vi kan jo late som om vi har gjort dette på en [TDD](https://en.wikipedia.org/wiki/Test-driven_development)-lignende måte.
For å teste transisjonfunksjonen vår, lager vi en `statemachine.test.ts`:
```typescript
import { Context, Effects, Events, States, transition } from "../src/statemachine";

describe("verify state machine transitions", () => {
  // Gi litt kortere navn på hendelser
  const [
    connect,
    // ... droppet resten for å være kortfattet
  ] = [
    Events.ON_CONNECT(),
    // ... droppet resten for å være kortfattet
  ];


  // States
  const initialContext: Context = {
    reconnectAttempt: 0,
    pingTimeoutMillis: 1,
    pongTimeoutMillis: 1
  };

  const initial = States.INITIAL(initialContext);
  const connecting = States.CONNECTING(initialContext);
  // ... droppet resten for å være kortfattet

  // Effects
  const fCon = Effects.CONNECT_WS();
  // ... droppet resten for å være kortfattet



  // Brukbar dekning av permutasjoner
  test.each`
    sourceState     | action         | targetState     | expectedEffects
    ${initial}      | ${connect}     | ${connecting}   | ${[fCon]}
    ${initial}      | ${open}        | ${initial}      | ${[]}
    ${initial}      | ${heartbeat}   | ${initial}      | ${[]}
    ${initial}      | ${pingtimeout} | ${initial}      | ${[]}
    ${initial}      | ${pongtimeout} | ${initial}      | ${[]}
    ${initial}      | ${close}       | ${initial}      | ${[]}
    ${initial}      | ${reconnect}   | ${initial}      | ${[]}
    ${connecting}   | ${open}        | ${opened}       | ${[fPingTO]}
    ${connecting}   | ${close}       | ${closed}       | ${[]}
    ${connecting}   | ${connect}     | ${connecting}   | ${[]}
    ${connecting}   | ${heartbeat}   | ${connecting}   | ${[]}
    ${connecting}   | ${pingtimeout} | ${connecting}   | ${[]}
    ${connecting}   | ${pongtimeout} | ${connecting}   | ${[]}
    ${connecting}   | ${reconnect}   | ${connecting}   | ${[]}
    ${opened}       | ${pingtimeout} | ${opened}       | ${[fSendPing, fPongTO]}
    ${opened}       | ${pongtimeout} | ${opened}       | ${[fClose]}
    ${opened}       | ${heartbeat}   | ${opened}       | ${[fClearPong, fPingTO]}
    ${opened}       | ${close}       | ${closed}       | ${[fClearPing, fClearPong]}
    ${opened}       | ${connect}     | ${opened}       | ${[]}
    ${opened}       | ${open}        | ${opened}       | ${[]}
    ${opened}       | ${reconnect}   | ${opened}       | ${[]}
    ${closed}       | ${reconnect}   | ${reconnecting} | ${[fConTO]}
    ${closed}       | ${connect}     | ${closed}       | ${[]}
    ${closed}       | ${open}        | ${closed}       | ${[]}
    ${closed}       | ${heartbeat}   | ${closed}       | ${[]}
    ${closed}       | ${pingtimeout} | ${closed}       | ${[]}
    ${closed}       | ${pongtimeout} | ${closed}       | ${[]}
    ${closed}       | ${close}       | ${closed}       | ${[]}
    ${reconnecting} | ${connect}     | ${connecting1}  | ${[fCon]}
    ${reconnecting} | ${reconnect}   | ${reconnecting} | ${[]}
    ${reconnecting} | ${open}        | ${reconnecting} | ${[]}
    ${reconnecting} | ${heartbeat}   | ${reconnecting} | ${[]}
    ${reconnecting} | ${pingtimeout} | ${reconnecting} | ${[]}
    ${reconnecting} | ${pongtimeout} | ${reconnecting} | ${[]}
    ${reconnecting} | ${close}       | ${reconnecting} | ${[]}
  `(
    "update with $sourceState gives $targetState and $expectedEffects",
    ({ sourceState, action, targetState, expectedEffects }) => {
      const [nextState, effects] = transition(action, sourceState);
      expect(nextState).toStrictEqual(targetState);
      expect(effects).toStrictEqual(expectedEffects);
    },
  );
});

```
Det er litt jobb å sette opp input-parametere og forventede output-verdier. For at ikke permutasjonstabellen skal bli for bred og vanskelig å lese,
valgte vi å lage korte navn. Vi måtte også gjøre litt ekstra arbeide med å definere effekter på forhånd. I sum ble det kanskje ikke så ille og det
er relativt lett å skanne over tabellen for å se at vi har dekket alle transisjonene.


## Klient-API
Før vi går løs på effekthåndteringen la oss først prøve å lage et grensesnitt for websocket-wrapperen vår.
Vi lager en fil `index.ts` som blir innfallsporten til biblioteket vårt.

```typescript
import {
  Context,
  Effect,
  Effects,
  States,
  Event,
  TimeoutKey,
  transition,
  Events,
  State
} from "./statemachine";

// Vi ønsker oss en konfigurasjonsparameter slik at klienten kan styre endel ting selv
// (Vi legger til to felter fra Context typen vår vha Pick typen i typescript og skjøter sammen med &)
export type Config = Pick<Context, "pingTimeoutMillis" | "pongTimeoutMillis"> & {
  url: string,

  // Callback for faktiske meldinger fra websocket-server
  // (pong-meldinger vil ikke trigge denne, da dette er et internt anliggende for wrapperen)
  onMessage: (msg: MessageEvent) => void,

  // Kan være hendig for klienten å få en callback ved tilstandsendringer
  onStateChange?: (previous: State, current: State) => void,
}

// Når man oppretter wrapperen vår får man et objekt med følgende signature i retur
export type WSMachine = {
  // Kobler til faktisk websocket og starter tilstandsmaskinen
  connect: () => void;

  // Kjekt å kunne sende meldinger til server!
  send: (data: string | ArrayBufferLike | Blob | ArrayBufferView) => void;

  // Hjelpefunksjon dersom klienten skulle ønske å spørre hva tilstanden er nå
  currentState: () => State;

  // Kobler ned websocket og stopper tilstandsmaskinen
  disconnect: () => void;
};


// Skall-implementasjon for å opprette en WSMachine
export const wsMachine = (config: Config): WSMachine => {
  // TODO: Effekthåndtering


  // TODO: Implementere API for WSMachine
  return {
    connect: () => { return },
    disconnect: () => { return },
    currentState: () => throw Error("Not implemented yet"),
    send: (data: string | ArrayBufferLike | Blob | ArrayBufferView) => {return}
  };
};
```

### Effekthåndtering
```typescript
// Type alias for returtypen til setTimeout funksjonen
type TTimeout = ReturnType<typeof setTimeout>;

export const wsMachine = (config: Config): WSMachine => {
  // Faktisk websocket
  let ws: WebSocket | undefined;

  // Initiell tilstand for tilstandsmaskin
  let wsState = States.INITIAL({...config, reconnectAttempt: 0});

  // Et map som hjelper oss å holde orden på timeouts
  const timeouts: Map<TimeoutKey, TTimeout> = new Map();

  // Hjelpefunksjon for å stoppe en timeout effekt
  const nukeTimeout = (k: TimeoutKey) => {
    const t = timeouts.get(k);
    if (t) {
      clearTimeout(t);
    }
  };

  // Når en websocket rapporterer at den er åpen
  const onOpen = () => handleTransition(Events.ON_OPEN());

  // Når vi får en melding fra websocket server
  const onMessage = (ev: MessageEvent) => {
    const msg = ev.data;
    // Vi ønsker ikke å plage klienten med pong meldinger
    if (msg !== "pong") {
      config.onMessage(ev);
    }
    // Uansett om det er pong eller en annen melding
    // Vi har fått puls fra server og er fornøyde
    handleTransition(Events.ON_HEARTBEAT());
  };

  // Når vi får beskjed om at WebSocket har blitt stengt
  // Uavhengig av grunn, vi gir oss ikke... og prøver å koble til på nytt!
  const onClose = () => {
    handleTransition(Events.ON_CLOSE());
    handleTransition(Events.ON_RECONNECT());
  };

  // Hjelpefunksjon for å håndtere hendelser som utløses av faktisk WebSocket
  const addEventListeners = () => {
    ws?.addEventListener("open", onOpen);
    ws?.addEventListener("close", onClose);
    ws?.addEventListener("message", onMessage);
  };


  const handleEffect = (effect: Effect) => {
    // TODO: Realisere en effekt
  }

  const handleTransition = (event: Event) => {
    // TODO: Håndtere en tilstandstransisjon og dens bestilte effekter
  }
```

Ok da har vi gjort unna mye av oppsettet vi trenger. La oss se på hvordan vi skal håndtere effektene.

```typescript
  const handleEffect = (effect: Effect) => {
    Effects.match(effect, {

      // Vi oppretter en WebSocket og legger til håndtering av hendelser på denne
      CONNECT_WS: () => {
        ws = new WebSocket(config.url);
        addEventListeners();
      },

      // Lukke websocket er jo bare å delegere til underliggende WebSocket
      CLOSE_WS: () => {
        ws?.close();
      },

      // Vi oppretter en timout, og legger den til i mappet vårt
      // Vi må nesten ta vare på de ett sted for å kunne stoppe de, når vi trenger det
      SCHEDULE_TIMEOUT: (t) => {
        timeouts.set(
          t.key,
          setTimeout(() => handleTransition(t.onTimeout), t.timeoutMillis),
        );
      },

      // Ping håndtering er jo bare å sende en melding til websocket server
      // Hva om du ønsker noe annet enn "ping"?
      // Det kan man f.eks løse ved å legge som en parameter på Config
      SEND_PING: () => {
        if (ws) {
          ws.send("ping");
        }
      },

      // Vi kaller hjelpefunksjonen vår få å stoppe en timeout fra å utløpe
      CLEAR_TIMEOUT: (t) => nukeTimeout(t.key)
    })
  }
```

Deretter må vi håndtere transisjonene og effektene som er bestilt.
```typescript
  const handleTransition = (event: Event) => {
    const [newState, effects] = transition(event, wsState);

    // Dersom klient har angitt at man ønsker å få beskjed om tilstandsendringer
    if (config.onStateChange) {
      // Denne kunne med fordel vært litt smartere.
      // Man burde sjekket om tilstandsnavnet faktisk har endret seg
      // Slik det er nå får man OPEN -> OPEN for pulskontroll hendelser...
      config.onStateChange(wsState, newState);
    }
    wsState = newState;

    // Håndterer evt. bestilte effekter
    effects.forEach((e) => {
      handleEffect(e);
    });
  }
```

Den siste TODO'en vi må ta tak i da er returobjektet som lar klienter stoppe og starte tilstandsmaskinen.

```typescript
  return {
    connect: () => handleTransition(Events.ON_CONNECT()),

    disconnect: () => {
      // nullstill tilstandsmaskinen
      wsState = States.INITIAL(wsState);


      // Rydd opp evt gjenlevende timeouts
      nukeTimeout("connect");
      nukeTimeout("ping");
      nukeTimeout("pong");

      // Lukk WebSocket og fjern funksjoner som lytter på hendelser på denne
      if (ws) {
        ws.removeEventListener("open", onOpen);
        ws.removeEventListener("close", onClose);
        ws.removeEventListener("message", onMessage);
        ws.close();
      }
    },

    currentState: () => wsState,

    send: (data: string | ArrayBufferLike | Blob | ArrayBufferView) => {
      if (States.is.OPENED(wsState) && ws instanceof WebSocket) {
        ws.send(data);

      // Dette er kanskje ikke så fryktelig elegant
      } else {
        const errorMsg = `Can't send message when websocket connection isn't in an open state. State is: ${
          wsState.tag
        }`;
        throw Error(errorMsg);
      }
    }
  };
```
Vi klatta på en `send` funksjon. Men dette kunne man kanskje ønsket å håndtere mer elegant. Kanskje vi kunne ha modellert
dette smartere? Det kunne kanskje også være ønskelig i endel tilfeller å bare bufre opp meldinger i påvente av at man
får forbindelse igjen. Hva tenker du?

### Testing av effekter
For å lage tester av effekthåndteringen vår benytter vi oss av en bibliotek for å mocke websockets.
```bash
npm install --save-dev jest-websocket-mock mock-socket
```

La oss lage en `index.test.ts`
```typescript
import WS from "jest-websocket-mock";
import { WSMachine, wsMachine } from "../src";

describe("verify wsMachine interactions", () => {
  let server: WS | undefined;
  let machine: WSMachine;
  let messages: string[] = [];
  let stateChanges: string[] = [];

  beforeEach(async () => {
    messages = [];
    stateChanges = [];

    // Lager en ny mock WebSocket server
    server = new WS("ws://localhost:1234");

    // Faker "pong" meldinger hver gang server mottar en melding
    server.on("message", (ws) => {
      ws.send("pong");
    });

    // Sett opp vår WSMachine
    machine = wsMachine({
      url: "ws://localhost:1234",
      pingTimeoutMillis: 10,
      pongTimeoutMillis: 10,
      onMessage(msg) {
        messages.push(msg.data);
      },
      onStateChange(prev, curr) {
        stateChanges.push(`${prev.tag}->${curr.tag}`);
      }
    });
    machine.connect();
  });

  afterEach(() => {
    // Rydd opp etter oss for hver test
    if (machine) { machine.disconnect(); }
    WS.clean();
  });

  test("connect to machine and verify ping", async () => {
    // Mock server har hendige promises vi kan vente på
    // Her venter vi på at klienten vår har koblet til
    await server?.connected;

    // vi venter til ping melding har blitt mottatt av server
    await expect(server).toReceiveMessage("ping");
    expect(server).toHaveReceivedMessages(["ping"]);
    expect(messages).toEqual([]);

    // Her sjekker vi at vi har fått de transisjonene vi forventer frem til nå
    expect(stateChanges).toEqual([
      "INITIAL->CONNECTING",
      "CONNECTING->OPENED",
      "OPENED->OPENED",
    ]);
  });

  test("connect to machine and verify message received", async () => {
    await server?.connected;
    server?.send("test");

    expect(messages).toEqual(["test"]);
  });

  test("server close triggers reconnect attempt(s)", async () => {
    // Vi venter til vi har koblet til
    // Så lukker vi forbindelsen fra serveren
    await server?.connected;
    server?.close();
    await server?.closed;

    // Her sjekker vi at vi automatisk prøver å kobler til igjen
    expect(stateChanges).toEqual([
      "INITIAL->CONNECTING",
      "CONNECTING->OPENED",
      "OPENED->CLOSED",
      "CLOSED->RECONNECTING"
    ]);
  });

});

test("no pong triggers reconnect", async () => {
  // I denne testen setter vi ikke opp noe automatisk retur av pong meldinger
  // I følge pulskontroll logikken vår skal dette føre til gjenoppkobling


  const server = new WS("ws://localhost:12345");
  const stateChanges: string[] = [];
  const machine = wsMachine({
    url: "ws://localhost:12345",
    pingTimeoutMillis: 5,
    pongTimeoutMillis: 5,
    onMessage: () => {
      return;
    },
    onStateChange(prev, curr) {
      stateChanges.push(`${prev.tag}->${curr.tag}`);
    }
  });
  machine.connect();

  await server.connected;

  // Vi forventer her at tilstandsmaskinen skal lukke forbindelsen
  // Siden vi ikke mottar en pong etter vi har sendt ping!
  await server.closed;

  expect(stateChanges).toStrictEqual([
    "INITIAL->CONNECTING",
    "CONNECTING->OPENED",
    "OPENED->OPENED", // ping timeout event
    "OPENED->OPENED", // pong timeout event

    // Disse to trigges av onClose
    "OPENED->CLOSED",
    "CLOSED->RECONNECTING"
  ]);

  machine.disconnect();
  WS.clean();
});
```

Dette er ikke en utfyllende test av alle mulige tilfeller, men det er nok til å illustrere hvordan man kan integrasjonsteste.
Det viser også hvor mye kjipere det er å teste funksjoner med sideeffekter.


## Backoff ved gjenoppkobling
Se for deg at du har brukt vårt flotte WebSocket bibliotek. Det er en kjempesuksess og du har sykt mange samtidige klienter.
Så tar du ned server(ene) dine for en oppgradering. Med vår naive logikk for gjenoppkobling vil du få en hærskare av klienter
som prøver å koble seg opp samtidig. De prøver med større og større mellomrom mellom hver gang de prøver, men fortsatt omtrent samtidig.
I det serveren(e) din endelig kommer opp igjen, vil det fort bli veldig mange klienter som kobler til omtrent samtidig. Det kan være litt kjipt.
Du kan lese mer om problemstilling og foreslåtte løsninger på internet. [F.eks denne artikkelen](https://www.baeldung.com/resilience4j-backoff-jitter).
Det vi burde gjøre er å lage en backoff algoritme som sørger for bedre spredning av gjenoppkoblingsforsøkene.

La oss gi det et forsøk:

```typescript
export const calcBackoff = (
  attempt: number,
  randSeed: number,
  maxVal = 30000,
): number => {
  if (attempt === 0) {
    return 0;
  }
  return Math.min(maxVal, (attempt ** 2) * 1000) + (2000 * randSeed);
};
```
Vi lager en backoff funksjon som øker eksponensielt. I tillegg legger vi til støy ved å bruke en random verdi vi tar inn som parameter.
Denne funksjonen er fortsatt veldig naiv, og gir fortsatt ikke veldig god/jevn spredning. Det får bli opp til vordende biblioteksforfattere
å lage en bedre implementasjon. Det er fortsatt langt bedre enn vår lineære backoff og det viktig er å illustrere konseptet.
Ok, så nå har vi en litt bedre backoff funksjon, men den forventer en random seed (ett desimaltall mellom 0 og 1). Hvordan får vi tak i en slik
random verdi? Vi ønsker jo ikke å tulle til transisjonsfunksjonen vår med sideeffekter, gjør vi vel?


### Effekter
```typescript
export const Effects = unionize({
  // ...eksisterende effekter utelatt

  // Vi definerer en ny effekt for å bestille en tilfeldig tall
  REQUEST_RANDOM: {}
});
```

### Hendelser
```typescript
export const Events = unionize({
  // ...eksisterende hendelser utelatt

  // Ny hendelse til tilstandsmaskinen vår som utløses når bestilling er effektuert!
  ON_RANDOM: ofType<{seed: number}>()
});

```

### Transisjoner
```typescript
  // ... resten som før


  CLOSED: () => Events.match(evt, {
    // Endring: I stedet for å bestille en timeout for å gjenoppkoble bestiller
    // vi her en tilfeldig verdi
    ON_RECONNECT: () => [
      States.RECONNECTING({...state, reconnectAttempt: state.reconnectAttempt + 1}),
      [Effects.REQUEST_RANDOM()]
    ],
    default: () => [state, []]
  }),

  RECONNECTING: () => Events.match(evt, {

    // Ny: Først når vi har en tilfeldig verdi kan vi beregne timeout
    // og bestille en timeout for gjenoppkobling
    ON_RANDOM: ({seed}) => [
      state,
      [Effects.SCHEDULE_TIMEOUT({
        key: "connect",
        // Vi bytter ut med vår nye fancy funksjon for å beregne timout
        timeoutMillis: calcBackoff(state.reconnectAttempt, seed),
        onTimeout: Events.ON_CONNECT()
      })]
    ],
    ON_CONNECT: () => [
      States.CONNECTING(state), [Effects.CONNECT_WS()]
    ],
    default: () => [state, []]
  })

```
Det var ikke så ille, selvom det var litt ekstra jobb selvfølgelig. Funksjonen vår er fortsatt "ren" og fri for sideeffekter.

### Effekthåndtering
```typescript
  const handleEffect = (effect: Effect) => {
    Effects.match(effect, {
      // eksisterende tilfeller utelatt


      REQUEST_RANDOM: () => {
        // Vi lener oss på Math.random() for å få en en tilfeldig verdi
        handleTransition(Events.ON_RANDOM({ seed: Math.random() }))
      }
    })
  };
```
Å implementere effekthåndtering var jo ikke så vanskelig. Nå skulle vi selvfølgelig ha oppdatert testene våre også, men det får bli
hjemmelekse. Det er på tide å runde av.


## Oppsummering
Da har vi kommet mer eller mindre i mål med å lage et lite bibliotek for å berike en standard WebSocket-klient. Vi har brukt
tilstandsmaskiner og funksjonell programmering som inspirasjon for implementasjonen vår. Det krever litt mer innsats å skille
rene funksjoner fra sideeffekter og koden ser kanskje ganske fremmed ut for mange. For meg er det verdt
innsatsen, men jeg har forståelse for at ikke alle nødvendigvis er enige med meg om det.

Koden bak min julegave til npm finner du på https://github.com/rundis/ts-ws-machine. Det er ikke 1-1 med koden i denne bloggposten,
men likner ganske mye.
