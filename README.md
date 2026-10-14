# FX EASY
A NeoForge mod for Minecraft 26.1.2 about FX, trading and finance.

## What it adds

- **FX Phone** (`fxeasy:fx_phone`): crafted from one iron ingot, gold ingot, emerald, dirt block, diamond,
  amethyst shard and copper ingot in any arrangement. Its icon is a book sprite with an energy swirl orbiting
  it, animated from eight frames of `assets/fxeasy/textures/item/fx_phone.png` through its `.mcmeta`, and
  warped by the mod pipeline `fxeasy:pipeline/fx_phone`, whose vertex shader pushes the icon geometry around
  and whose fragment shader refracts the sampled texels, so the icon ripples wherever it is drawn.
- **久留美集团 terminal**: right click the phone (or the client tests open it directly) to open the trading
  terminal - a full screen dashboard drawn with vanilla GUI primitives. It opens on the market list, one row
  per pair with both currency items, the last price, the change over the selected timeframe (1m, 5m or 15m of
  game ticks) and a sparkline of the same window; picking a row opens that pair's chart with the order ticket
  beside it, the positions and account pages carry the booking and the session equity curve, and a fifth page,
  **Shop**, sells the platform's subscriptions. The ticket
  takes the side, the size in lots - one slider that reaches from nothing to what the free margin of the chosen
  margin currency backs at the leverage of the order, or to what the account's own risk quota on either currency
  of the pair still leaves, whichever is smaller, so it can never build a size the platform would refuse for
  want of margin or for want of the account's quota - the leverage and the currency the order locks its margin
  in, and quotes what the order
  is worth in the quote currency and the margin it would need in the margin currency, so a player holding dirt
  can back a dirt/netherite trade with dirt. Every amount the panel shows is an item count: balances, margin,
  profit and the size of a position are items of one currency, and
  the aggregates that are sums over currencies - equity, used margin, unrealised profit - are quoted in items
  of one currency the player picks in the panel, converted at the live rate and starting on the first currency
  the world defines. The chart switches between candles with volume bars and a line with an area
  fill, hovering either style reads out the point under the cursor, and the whole panel lays itself out in
  design units at two physical pixels per unit,
  so it keeps the same density and a crisp font at any window size and GUI scale. Opening the panel scans it in
  over the first fraction of a second - a click skips the rest. An account that has not been opened yet is met
  by the opening: a card with the risk disclosure, the terms the platform trades on and a registration form for the
  holder's name and age - below 20 the form refuses to be filed, which is a joke rather than a check. The
  registration is remembered per account once accepted, and a player who has not traded before is then walked
  through the five areas of the dashboard once, which the server remembers as well. The server refuses orders,
  deposits and withdrawals on an account that has not been opened, so the flow cannot be skipped, and the
  first time an account opens the terminal the platform offers it its welcome capital - ten diamonds, once per
  account, which is the money a player trades with before mining anything. The platform says so itself: a card
  in the shape of a broker's account bonus carries the amount, what it trades on, the button that claims the
  money and the small print. Nothing moves until the button is clicked, the button is gone from then on, and
  any other click on the card, or Escape, puts it away without claiming anything. The control
  row also carries an intel cell, which opens the world intel overlay: the economy report (what every supported
  source produced, what the terminal reabsorbed, the pressure level of each currency and how much of the
  revaluation is already priced), the public message feed with the phase, quality and channel of each report,
  and the attribution of the priced part between the fundamental and the event component. The three operations
  that cannot be taken back - closing every position, a deposit and a withdrawal - ask once in a confirmation
  card before they are sent. The account tab moves items in and out of the platform with one slider that only
  reaches as far as the action could go - the items the player carries for a deposit, the free margin for a
  withdrawal - starts on the items the player carries and follows what they pick up or hand over, and reports
  the annual interest and the haircut of the currency it acts on.
- **Legal tender**: the world defines its own currencies in `serverconfig/fxeasy-currencies.json` - id, ticker,
  display name and the names it answers to in other languages, the item that backs it, how many atoms one of its
  items is worth, the price the world opens on, the daily volatility, the annual rate and the capacity figures. A
  world without that file is handed the five this economy opened with: dirt, gold ingot, emerald, diamond and
  netherite ingot. One item is one unit of a
  balance and the only amount the platform deals in; a currency an item backs is deposited and withdrawn in
  whole items of it, and one without an item is still traded but never leaves the platform. Dirt pays no
  interest; the others carry an annual rate of 2% to 8%, and that rate is what the overnight swap and the
  drift of their price against dirt are built from.
- **Trading**: every pair of the currencies a world defines, long and short, 1x to 100x leverage (the shop sells
  a higher ceiling), margin in whichever of them the order names. An order is an item count of the base currency of its pair, one item of
  which is one lot - the unit the ticket reads its size out in, and the smallest size the platform takes - so
  the same size is a different position in every pair and the notional of an order is what those items are
  worth at the live rate. The margin currency is the one whose items the order locks, and it carries the
  position's swap and profit;
  backing a pair with anything other than the currency it settles in costs the collateral's haircut (four times
  its daily volatility) and a 0.1% conversion spread. The costs are the
  spread, the daily swap - paid to the side that holds the higher yielding currency and taken from the other,
  with a 25% markup on the interest difference - and occasional adverse slippage, all widening with the
  volatility regime. Margin call at 100% of equity over used margin, forced liquidation at 50%. On top of that
  the platform limits how much absolute risk one account, and the server as a whole, may hold in one currency;
  an order that would add more than the limit is refused, while anything that reduces risk is always allowed.
  What the limit of the account still leaves it is what the ticket's size slider reaches at the most, because
  the platform reports that room with every account snapshot; the room left on the server as a whole stays the
  server's own, so an order that runs into it is refused and reported.
- **Subscriptions**: the shop page of the terminal sells three tiers, priced by the catalogue in the currency each is quoted in - Go for 8888 dirt, Plus for 64 diamonds, Pro for 666 diamonds - and paid out of the free margin of the account at a checkout the card opens rather than at the card itself: the receipt writes the tier and the length of its period, the list price, the rate the payment converts at, the amount that converts to, what the conversion costs on top and the total, and the player settles it in whichever of the currencies they hold a balance in, picked from the rows under the receipt. A payment in the currency the plan is priced in costs the list price; a payment in another one is that price converted at the live rate of the market plus the conversion spread the platform charges for turning one currency into another, scaled by the plan the account already trades on, so a Pro account pays a third of the spread a new one does. Only the free margin of the currency being paid with counts, a row whose balance does not cover the total is drawn as short and the button under it refuses what the platform would refuse, and the renewal of the period charges the currency the catalogue prices the plan in, whatever the period before it was settled in. Every tier is sold on a card that carries the picture of its own character behind the whole of the tier - cropped to the card rather than stretched, so the figure stays whole, and blended into the card so the wording stands on it - with the name and the state at its top, the line of lore it is sold with (`芽吹的选择`, `萌智子的选择`, `久留美的选择`), the price of a period next to the icon of the currency and the length of that period under them, and then what the tier buys line by line: the ceiling it puts on the leverage of an order (200, 300 and 500 over the retail 100) and one line per cost the platform charges - the spread of a fill, the slippage that may widen it, the swap of an overnight position and the conversion the platform takes on collateral of another currency - each with what the plan takes off it, which is a third for Plus and two thirds for Pro, and the plain rate for Go, with the note saying what that cost is where the card has the room. The top tier writes its lounge out as well: the pace of its gifts and the pool that lounge draws from, gift by gift with the share of the draws each of them takes, scrolled by the wheel when the list runs past the card. Over the picture stands a sheen and the figure of its tier, drawn strongest on the plan the account is on: a warm sweep with a vortex turning over a Go card, an iridescent one with flames licking up a Plus card, and a holographic one with counter-moving bands, scan lines and a thunder bolt striking over Pro. One period is fifteen game days and the platform renews it by itself while the balance covers the price; a period that cannot pay lapses instead, so a subscription a player stops paying for runs out rather than into debt. Cancelling is a switch on the shop page - it stops the next charge and nothing else, because a period that was paid is never taken back. The top tier opens the **VIP lounge**: one gift per economy day while the plan runs, drawn from a pool the world defines in `serverconfig/fxeasy-lounge.json` and kept until it is claimed, each of them a real stack - by default the valuables, the potions and the food a client of the group would want - handed into the inventory from the lounge card.
- **Debt and credit**: a close or a liquidation that leaves a balance below zero is debt, and the platform
  charges 11% of what is owed every three game days, adding the charge to the debt so the next period is
  charged on the interest as well. A debt that is still outstanding after ten game days blacklists the credit
  record: the account may no longer open anything until it is repaid in full, while closing, depositing and
  withdrawing stay allowed, because they are the only way a debt is ever paid. The debt ages on the world
  clock rather than on a login, so it grows while its trader is away.
- **World economy**: the market is a projection of what the world actually does. Zombie piglin gold, emerald
  a villager pays, natural ore that a player really mined out of the ground and the gold a piglin really
  accepted for a barter are committed facts, counted in whole atoms (a gold nugget is one atom, an ingot nine)
  and priced by an economy model that saturates: sustained output revalues a currency down over days and then
  settles at a new level instead of falling forever, and production that stops clears its inventory pressure
  slowly. Capacity - what the market treats as normal output - scales with the players actually playing, so
  ten seconds of extra logins cannot move it. Transfers never count: picking items up, moving them in hoppers,
  depositing them into the platform, unloading and reloading a chunk and taking them back out again all
  contribute nothing, and a resource block a player placed is never a natural find.
- **Market events**: the terminal carries a news feed of what the platform announced - a raid that disrupted a
  trade network, the first ender dragon kill, a shipment rumour, a piglin festival - each with the quality of
  the information (observed, estimated, rumour, or plainly a joke), the currencies it concerns, the size it is
  allowed to reach and the phase it is in. Production reports quote what the world already produced instead of
  pricing it a second time, a rumour that turns out to be false gives back the premium it had applied, and a
  news-only message moves nothing at all. The market's volatility quotes widen for risk the platform has
  already published, never for one it has not.
- **Pricing**: every currency's log price is an anchor that drifts by its interest rate and takes a small
  residual shock, plus a fundamental revaluation the world economy has applied, plus a bounded event
  component. A pair's price is the ratio of the two, so cross rates stay consistent. Nothing pulls a
  currency back to its genesis level: a factory that keeps producing holds its currency at the lower level it
  earned, and the hard band a factor of 16 wide is only a stop for a world that runs for years.
- **Persistence and determinism**: the market runs on its own monotonic clock counted in advanced server
  ticks, so sleeping or a time command cannot skip a market day or an event, and the price, event and order
  execution generators are three separate states, so trading cannot pick the next piece of news. All accounts,
  positions, candles, the economy report and the event layer live in one `SavedData`, and a load resumes on
  exactly the price it stopped at, the world economy's revaluation included; a world that predates this
  extension keeps its prices, its accounts and its day continuity and starts observing from the load, without
  back filling a history nobody recorded.
- **Configuration**: `config/fxeasy-server.toml` in the world's `serverconfig` directory carries the capacity
  figures, the collection switches, the event frequency, the exposure limits, whether the global limit
  applies at all and which currency the welcome capital is paid in with how many items of it. Changing it
  retunes the market at the next economy bucket rather than mid tick; the currency set itself lives in the
  `fxeasy-currencies.json` of the world's `serverconfig` directory and is read when the world starts, and the pool
  the VIP lounge draws from is the `fxeasy-lounge.json` beside it: the gifts with their weights, the market ticks
  between two of them and how many the lounge may hold.

## Requirements
- JDK 25 (`JAVA_HOME` must point at it, Gradle toolchain resolves it automatically)
- Git Bash on Windows

## Build and test
```sh
bash gradlew build            # compile and assemble the mod jar
bash gradlew test             # JUnit Jupiter unit tests (pure logic)
bash gradlew runGameTestServer # game tests (server side logic)
bash gradlew runData          # regenerate assets/data into src/generated/resources
bash gradlew runClient        # development client
bash scripts/client-e2e.sh # end-to-end client test: the client opens and walks the terminal itself and the script asserts its log lines
MCRT_RENDER=1 bash scripts/client-e2e.sh # same with real rendering, and the panel layout has to appear (needs an OpenGL 3.2 context)
bash scripts/client-regression.sh # full chain regression in a development client: walks every page, overlay and money path and asserts the panel after every step
```

See `AGENTS.md` for the code, data and testing conventions.
