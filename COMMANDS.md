# Commands

Merchants has one command, `/merchant`, with a subcommand for each action. Each subcommand has its own permission node, and every node defaults to server operators.

| Command | Permission | What it does |
|---|---|---|
| `/merchant create <name>` | `merchants.create` | Places a merchant villager named `<name>` where you stand. Only a player can run it. |
| `/merchant delete <name>` | `merchants.delete` | Removes the merchant named `<name>` and its villager. |
| `/merchant list` | `merchants.list` | Lists every merchant with its world and block coordinates. |

Running `/merchant` with no subcommand, or with one it does not recognise, prints the usage line.

## Notes

- **Names.** Names are matched ignoring case, and must be unique. A name may contain spaces: everything after the subcommand is taken as the name, so `/merchant create Old Baker` creates a merchant named `Old Baker`.
- **Deleting in an unloaded area.** If the merchant's villager is in a chunk that is not loaded, `/merchant delete` refuses and tells you where the merchant is. Deleting only the record would leave an invulnerable villager standing in the world. Go to the merchant, or anywhere that loads its chunk, and run the command again. If the villager is no longer in the world at all, or its world no longer exists, the record is deleted anyway.
- **Not yet available.** `/merchant edit` is planned and waits on the stock list ([#9](https://github.com/Dans-Plugins/Merchants/issues/9)).
