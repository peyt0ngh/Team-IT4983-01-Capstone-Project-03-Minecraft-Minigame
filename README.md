Dungeon Explorers Minigame for KSU IT4983

Created by: Ryan Heidel, Preston Clark, Peyton Rihner, Surah Muhammad and Zara Khusro

Place provided dungeon-explorers-1.0.0.jar file into server plugins folder of world, preferably an empty world due to hardcoded coordinate variables.

Commands:

/mgstart [singleplayer/multiplayer] - Initiates game and generates dungeon
/mgstop - Ends active game session; used in end of game procedure (stage 3 is completed, timer runs out, player dies in singleplayer, etc.)
/mgscore - Displays current scoreboard of game session, also used at end of game

/mgstageclear - Debug command to instantly advance stage to next, allows for custom input of time remaining
/mgaddtreasure - Debug command to provide player with treasure of 4 specified rarities (common, uncommon, rare and super rare)
