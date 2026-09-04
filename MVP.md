# Merchants — MVP

## Problem

Players asked for "NPCs for buying and selling". Underneath that is a real gap: a server can have a currency and still have no economy, because there is nowhere to trade when the other players are offline. Vanilla villager trading exists but trades in emeralds, restocks on its own schedule, and cannot be told what a thing is worth.

## Target environment

Spigot, Minecraft 26.1, **vanilla clients**. A merchant therefore has to *be* an entity the client already knows how to draw. v1 uses a villager, because a villager already reads as a shopkeeper and already opens a trading interface.

## The smallest shippable slice

### v1 does

1. **Create a merchant.** An operator spawns a merchant at a location, names it, and it persists across restarts. It does not wander, despawn, breed, panic, transform into a witch, or get converted by a zombie.
2. **Give it a stock list.** A per-merchant list of entries: an item, a buy price, a sell price, and optionally a stock limit. Defined in configuration; a GUI editor can come later.
3. **Trade for coin.** Opening a merchant shows what it buys and sells. Buying deducts from the player's [Medieval Economy](https://github.com/Dans-Plugins/Medieval-Economy) coinpurse; selling credits it. Prices are in coins, not emeralds.
4. **Restock.** A merchant with a stock limit refills on a configurable interval.
5. **Protect merchants** from damage, so a market is not one creeper away from gone.
6. **`/merchant`** to create, delete, list and edit.

### v1 does not

- **Depend on Citizens.** A hard dependency on a large third-party NPC framework is a maintenance burden and a version-compatibility risk for one villager that stands still.
- **Give merchants custom skins, names above their heads beyond a nameplate, or dialogue.**
- **Wander or travel.** No caravans, no wandering traders, no restocking runs between settlements.
- **Simulate supply and demand.** Prices are what the operator set them to. Dynamic pricing is a genuinely interesting v2 and a guaranteed v1 delay.
- **Let players own merchants.** v1 merchants are operator-placed server infrastructure. Player-owned shops raise ownership, theft, taxation and inflation questions all at once.
- **Integrate with Medieval Factions taxation or claim permissions.**
- **Trade in anything but the Medieval Economy currency.**

## Why these non-goals

The thing that makes an NPC shop useful is that it is *there*, reliably, with a price. Everything past that — skins, wandering, dynamic prices, player ownership — is a second plugin wearing this one's name. Player-owned shops in particular should be a deliberate later decision, because they are how a currency gets inflated to worthlessness.

## Open questions

- Does the villager trading UI actually work here, or does v1 need a custom inventory GUI? Vanilla trades are item-for-item; trading for a coin *item* may map onto it directly, and trading against a virtual coinpurse balance almost certainly does not. This is the first thing to prototype.
- Medieval Economy is currently single-denomination (`currencyItemName`, default "Gold Coin"). If it gains copper/silver/gold tiers — which the same player request asks for — merchant pricing has to handle change-making. Worth knowing which way that lands before pricing is designed.
- Should a merchant's stock be a real chest in the world, so restocking is a job someone does rather than a timer?
- Do merchants need to respect claim permissions, so a faction cannot have a rival's market bulldozed?

## Dependencies

- [Medieval Economy](https://github.com/Dans-Plugins/Medieval-Economy) — **hard dependency.** There is no v1 without a currency to trade in.
