package com.ugcleague.ops.service.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;

public class NuclearThrone {

    /**
     * Characters are 0-indexed
     **/
    public static final List<String> CHARACTERS = asList(
        "Random",
        "Fish",
        "Crystal",
        "Eyes",
        "Melting",
        "Plant",
        "Yung Venuz",
        "Steroids",
        "Robot",
        "Chicken",
        "Rebel",
        "Horror",
        "Rogue",
        "Big Dog",
        "Skeleton",
        "Frog",
        "Cuz"
    );

    public static final Map<String, List<String>> CHARACTER_TIPS = new LinkedHashMap<String, List<String>>() {
        {
            put("Random", asList(
                "No one compares",
                "Active: anything",
                "Passive: anything",
                "Shapeless",
                "Never the same",
                "Clearly the most powerful",
                "Random shifts shapes"
            ));
            put("Fish", asList(
                "The taste of mud",
                "Like Kevin Costner",
                "Gills on your neck",
                "It's ok to eat",
                "Duty calls",
                "Last day before retirement"
            ));
            put("Crystal", asList(
                "Crystal can handle this",
                "Family"
            ));
            put("Eyes", asList(
                "Telekinesis pushes projectiles away",
                "Eyes can't speak",
                "All these thoughts",
                "Don't blink",
                "Eyes sees everything"
            ));
            put("Melting", asList(
                "It's ok to be scared",
                "Brr...",
                "Cough",
                "Everything hurts",
                "Melting is tired",
                "It's so cold out here"
            ));
            put("Plant", asList(
                "Gotta go fast",
                "Snare is a source of light",
                "Plant can hold RMB to see further",
                "Photosynthesizing...",
                "No mercy",
                "Blood blood blood",
                "Death death death",
                "Kill kill kill"
            ));
            put("Yung Venuz", asList(
                "Pop pop",
                "No popo",
                "Guns that wear vests it",
                "Guns that hate texas",
                "Guns with 6 senses",
                "Guns that straight festive",
                "Guns that make breakfast",
                "Guns that send texts",
                "Guns for fake necklace",
                "Mony",
                "2 Yung 2 Die",
                "Hashtag verifyvenuz",
                "Thanks Gun God",
                "So cool",
                "Yung Venuz is the best",
                "Yung Venuz is so cool"
            ));
            put("Steroids", asList(
                "Read a book",
                "Get strong",
                "Get shots",
                "Appreciate revolvers",
                "Steroids used to be a scientist",
                "Steroids could do pushups forever",
                "Study hard",
                "Time to flex"
            ));
            put("Robot", asList(
                "Don't forget to eat weapons",
                "I'm afraid I can't let you do that",
                "Guns for breakfast",
                "Tasty",
                "<3",
                "Robot",
                "Kill all humans"
            ));
            put("Chicken", asList(
                "Throw damage scales with your level",
                "Getting decapitated reduces max HP",
                "Again",
                "Amateur hour is over",
                "Never surrender",
                "Go",
                "Focus",
                "Remember the training"
            ));
            put("Rebel", asList(
                "The scarf is nice",
                "Allies take damage over time",
                "Your first ally costs less HP",
                "Spawning new allies heals old ones",
                "It will get better",
                "A new generation",
                "Allies are a source of light",
                "Change is coming",
                "Forget the old days"
            ));
            put("Horror", asList(
                "Firing the beam pauses rad attraction",
                "Enemies absorb the beam's rads",
                "Horror's beam destroys projectiles",
                "Horror's beam powers up over time",
                "Power",
                "The horror",
                "In the zone",
                "Radiation is everywhere"
            ));
            put("Rogue", asList(
                "Keep moving",
                "Never look back",
                "Never slow down",
                "They're getting closer",
                "Never stop",
                "Another world lost"
            ));
            put("Skeleton", asList(
                "Hard",
                "Dusty",
                "Dry",
                "Nothing..."
            ));
            put("Frog", asList(
                "Wait for me",
                "Don't hold it up",
                "Keep going",
                "Let it all out",
                "Gass is good",
                "Bloated",
                "Restless",
                "Let's go",
                "Can't wait"
            ));
            put("Cuz", asList(
                "Nice",
                "Cool"
            ));
        }
    };

    public static final List<String> LOW_HEALTH_TIPS = asList(
        "Help",
        "No no no",
        "Good luck",
        "This isn't going to end well",
        "Oh dear"
    );

    public static final List<String> LOOP_TIPS = asList(
        "No mountain too high",
        "No valley too deep",
        "We'll reach for the sky",
        "There's no limit",
        "What's happening",
        "Monsters everywhere",
        "It's a whole new world",
        "This will never end"
    );

    public static final Map<String, List<String>> MODE_TIPS = new LinkedHashMap<String, List<String>>() {
        {
            put("daily", asList(
                "One day",
                "There's always tomorrow",
                "One shot",
                "Don't mess it up",
                "The weather isn't so bad",
                "Such a nice day"
            ));
            put("weekly", asList(
                "Keep Trying",
                "Well prepared",
                "Free time",
                "What's next",
                "This seems familiar"
            ));
            put("hard", asList(
                "It can't be that bad",
                "Take your time",
                "Behind you",
                "What does SFMT stand for?",
                "Impossible",
                "No way",
                "Heh"
            ));
        }
    };

    /**
     * Ultras are 1x1-indexed (character, index)
     **/
    public static final List<List<String>> ULTRAS = asList(
        asList("Confiscate", "Gun Warrant"),
        asList("Fortress", "Juggernaut"),
        asList("Projectile Style", "Monster Style"),
        asList("Brain Capacity", "Detachment"),
        asList("Trapper", "Killer"),
        asList("Ima Gun God", "Back 2 Bizniz"),
        asList("Ambidextrous", "Get Loaded"),
        asList("Refined Taste", "Regurgitate"),
        asList("Harder to kill", "Determination"),
        asList("Personal Guard", "Riot"),
        asList("Stalker", "Anomaly", "Meltdown"),
        asList("Super Portal Strike", "Super Blast Armor")
    );

    public static final Map<String, List<String>> ULTRA_TIPS = new LinkedHashMap<String, List<String>>() {
        {
            put("Fish", asList("Unstoppable",
                "Just one more day",
                "Getting used to this"));
            put("Crystal", asList("Stay strong",
                "Just a scratch"));
            put("Eyes", asList("Keep it inside",
                "Show nothing",
                "Know everything"));
            put("Melting", asList("Please stop...",
                "The pain..."));
            put("Plant", Collections.singletonList("End end end"));
            put("Yung Venuz", asList("Vote 2 B cool",
                "Go hard",
                "4ever",
                "YV fact: YV is the best",
                "Real thugs hustle",
                "One of these days",
                "Hashtag blessed",
                "Airsiren.wav",
                "Hashtag verifycuz"));
            put("Steroids", asList("So strong",
                "Let's hope this is correct",
                "Don't panic"));
            put("Robot", asList("Singularity",
                "Flesh is weak",
                "Machines will never end"));
            put("Chicken", asList("Again we are defeated",
                "Just like in the movies",
                "This is destiny"));
            put("Rebel", asList("No stopping now",
                "All together now",
                "Things are different"));
            put("Horror", asList("The light moves",
                "The air is changing"));
            put("Rogue", asList("They can't chase you forever",
                "You deserve this",
                "Use this",
                "Fire at will",
                "None will pass",
                "Guard this land"));
            put("Skeleton", asList("No need for peace",
                "Avoid the living",
                "This is better"));
            put("Frog", asList("Smell great",
                "Go forever",
                "Sweet sounds",
                "Singing",
                "Bwahahaha"));
        }
    };

    /**
     * Mutations are 0-indexed
     **/
    public static final List<String> MUTATIONS = asList(
        "Heavy Heart",
        "Rhino Skin",
        "Extra Feet",
        "Plutonium Hunger",
        "Rabbit Paw",
        "Throne Butt",
        "Lucky Shot",
        "Bloodlust",
        "Gamma Guts",
        "Second Stomach",
        "Back Muscle",
        "Scarier Face",
        "Euphoria",
        "Long Arms",
        "Boiling Veins",
        "Shotgun Shoulders",
        "Recycle Gland",
        "Laser Brain",
        "Last Wish",
        "Eagle Eyes",
        "Impact Wrists",
        "Bolt Marrow",
        "Stress",
        "Trigger Fingers",
        "Sharp Teeth",
        "Patience",
        "Hammer Head",
        "Strong Spirit",
        "Open Mind"
    );

    /**
     * Crowns are 1-indexed
     **/
    public static final List<String> CROWNS = asList(
        "Bare Head",
        "Crown of Death",
        "Crown of Life",
        "Crown of Haste",
        "Crown of Guns",
        "Crown of Hatred",
        "Crown of Blood",
        "Crown of Destiny",
        "Crown of Love",
        "Crown of Risk",
        "Crown of Curses",
        "Crown of Luck",
        "Crown of Protection"
    );

    public static final Map<String, List<String>> CROWN_TIPS = new LinkedHashMap<String, List<String>>() {
        {
            put("Crown of Death", asList("Health is important", "Boom"));
            put("Crown of Guns", Collections.singletonList("Guns are your friend"));
            put("Crown of Hatred", Collections.singletonList("Something is wrong"));
            put("Crown of Destiny", Collections.singletonList("No such thing as free will"));
            put("Crown of Curses", Collections.singletonList("Why"));
            put("Crown of Protection", Collections.singletonList("Safety First"));
            put("Crown of Life", Collections.singletonList("Heart matters"));
            put("Crown of Love", Collections.singletonList("You really like these weapons"));
            put("Crown of Blood", asList("Bring it", "Get ready"));
            put("Crown of Luck", asList("It's all the same", "The future brings death"));
            put("Crown of Haste", Collections.singletonList("No time for jokes"));
            put("Crown of Risk", Collections.singletonList("Good"));
        }
    };

    /**
     * Weapons are 0-indexed
     */
    public static final List<String> WEAPONS = asList("Nothing",
        "Revolver",
        "Triple Machinegun",
        "Wrench",
        "Machinegun",
        "Shotgun",
        "Crossbow",
        "Grenade Launcher",
        "Double Shotgun",
        "Minigun",
        "Auto Shotgun",
        "Auto Crossbow",
        "Super Crossbow",
        "Shovel",
        "Bazooka",
        "Sticky Launcher",
        "SMG",
        "Assault Rifle",
        "Disc Gun",
        "Laser Pistol",
        "Laser Rifle",
        "Slugger",
        "Gatling Slugger",
        "Assault Slugger",
        "Energy Sword",
        "Super Slugger",
        "Hyper Rifle",
        "Screwdriver",
        "Laser Minigun",
        "Blood Launcher",
        "Splinter Gun",
        "Toxic Bow",
        "Sentry Gun",
        "Wave Gun",
        "Plasma Gun",
        "Plasma Cannon",
        "Energy Hammer",
        "Jackhammer",
        "Flak Cannon",
        "Golden Revolver",
        "Golden Wrench",
        "Golden Machinegun",
        "Golden Shotgun",
        "Golden Crossbow",
        "Golden Grenade Launcer",
        "Golden Laser Pistol",
        "Chicken Sword",
        "Nuke Launcher",
        "Ion Cannon",
        "Quadruple Machinegun",
        "Flamethrower",
        "Dragon",
        "Flare Gun",
        "Energy Screwdriver",
        "Hyper Launcher",
        "Laser Cannon",
        "Rusty Revolver",
        "Lightning Pistol",
        "Lightning Rifle",
        "Lightning Shotgun",
        "Super Flak Cannon",
        "Sawed-off Shotgun",
        "Splinter Pistol",
        "Super Splinter Gun",
        "Lightning SMG",
        "Smart Gun",
        "Heavy Crossbow",
        "Blood Hammer",
        "Lightning Cannon",
        "Pop Gun",
        "Plasma Rifle",
        "Pop Rifle",
        "Toxic Launcher",
        "Flame Cannon",
        "Lightning Hammer",
        "Flame Shotgun",
        "Double Flame Shotgun",
        "Auto Flame Shotgun",
        "Cluster Launcher",
        "Grenade Shotgun",
        "Grenade Rifle",
        "Rogue Rifle",
        "Party Gun",
        "Double Minigun",
        "Gatling Bazooka",
        "Auto Grenade Shotgun",
        "Ultra Revolver",
        "Ultra Laser Pistol",
        "Sledgehammer",
        "Heavy Revolver",
        "Heavy Machinegun",
        "Heavy Slugger",
        "Ultra Shovel",
        "Ultra Shotgun",
        "Ultra Crossbow",
        "Ultra Grenade Launcher",
        "Plasma Minigun",
        "Devastator",
        "Golden Plasma Gun",
        "Golden Slugger",
        "Golden Splinter Gun",
        "Golden Screwdriver",
        "Golden Bazooka",
        "Golden Assault Rifle",
        "Super Disc Gun",
        "Heavy Auto Crossbow",
        "Heavy Assault Rifle",
        "Blood Cannon",
        "Dog Spin Attack",
        "Dog Missile",
        "Incinerator",
        "Super Plasma Cannon",
        "Seeker Pistol",
        "Seeker Shotgun",
        "Eraser",
        "Guitar",
        "Bouncer SMG",
        "Bouncer Shotgun",
        "Hyper Slugger",
        "Super Bazooka",
        "Frog Pistol",
        "Black Sword",
        "Golden Nuke Launcher",
        "Golden Disc Gun",
        "Heavy Grenade Launcher",
        "Gun Gun",
        "Golden Frog Pistol"
    );

    public static final Map<Integer, String> ENEMIES = new LinkedHashMap<Integer, String>() {
        {
            put(0, "Bandit");
            put(1, "Maggot");
            put(2, "Rad Maggot");
            put(3, "Big Maggot");
            put(4, "Scorpion");
            put(5, "Gold Scorpion");
            put(6, "Big Bandit");
            put(7, "Rat");
            put(8, "Rat King");
            put(9, "Green Rat");
            put(10, "Gator");
            put(11, "Ballguy");
            put(12, "Toxic Ballguy");
            put(13, "Ballguy Mama");
            put(14, "Assassin");
            put(15, "Raven");
            put(16, "Salamander");
            put(17, "Sniper");
            put(18, "Big Dog");
            put(19, "Spider");
            put(20, "New Cave Thing");
            put(21, "Laser Crystal");
            put(22, "Hyper Crystal");
            put(23, "Snow Bandit");
            put(24, "Snowbot");
            put(25, "Wolf");
            put(26, "Snowtank");
            put(27, "Lil Hunter");
            put(28, "Freak");
            put(29, "Explo Freak");
            put(30, "Rhino Freak");
            put(31, "Necromancer");
            put(32, "Turret");
            put(33, "Technomancer");
            put(34, "Guardian");
            put(35, "Explo Guardian");
            put(36, "Dog Guardian");
            put(37, "Throne");
            put(38, "Throne 2");
            put(39, "Bone Fish");
            put(40, "Crab");
            put(41, "Turtle");
            put(42, "Venus Grunt");
            put(43, "Venus Sarge");
            put(44, "Fireballer");
            put(45, "Super Fireballer");
            put(46, "Jock");
            put(47, "Cursed Spider");
            put(48, "Cursed Crystal");
            put(49, "Mimic");
            put(50, "Health Mimic");
            put(51, "Grunt");
            put(52, "Inspector");
            put(53, "Shielder");
            put(54, "Crown Guardian");
            put(55, "Explosion");
            put(56, "Small Explosion");
            put(57, "Fire Trap");
            put(58, "Shield");
            put(59, "Toxin");
            put(60, "Horror");
            put(61, "Barrel");
            put(62, "Toxic Barrel");
            put(63, "Golden Barrel");
            put(64, "Car");
            put(65, "Venus Car");
            put(66, "Venus Car Fixed");
            put(67, "Venuz Car 2");
            put(68, "Icy Car");
            put(69, "Thrown Car");
            put(70, "Mine");
            put(71, "Crown of Death");
            put(72, "Rogue Strike");
            put(73, "Blood Launcher");
            put(74, "Blood Cannon");
            put(75, "Blood Hammer");
            put(76, "Disc");
            put(77, "Curse Eat");
            put(78, "Big Dog Missile");
            put(79, "Halloween Bandit");
            put(80, "Lil Hunter Death");
            put(81, "Throne Death");
            put(82, "Jungle Bandit");
            put(83, "Jungle Assassin");
            put(84, "Jungle Fly");
            put(85, "Crown of Hatred");
            put(86, "Ice Flower");
            put(87, "Cursed Ammo Pickup");
            put(88, "Underwater Lightning");
            put(89, "Elite Grunt");
            put(90, "Blood Gamble");
            put(91, "Elite Shielder");
            put(92, "Elite Inspector");
            put(93, "Captain");
            put(94, "Van");
            put(95, "Buff Gator");
            put(96, "Generator");
            put(97, "Lightning Crystal");
            put(98, "Golden Snowtank");
            put(99, "Green Explosion");
            put(100, "Small Generator");
            put(101, "Golden Disc");
            put(102, "Big Dog Explosion");
            put(103, "IDPD Freak");
            put(104, "Throne 2 Death");
            put(105, "Oasis Boss");
            put(-1, "Nothing");
        }
    };

    public static final Map<Integer, String> WORLDS = new LinkedHashMap<Integer, String>() {
        {
            put(0, "Campfire");
            put(1, "Desert");
            put(2, "Sewers");
            put(3, "Scrap yard");
            put(4, "Caves");
            put(5, "Frozen city");
            put(6, "Labs");
            put(7, "Palace");
            put(100, "Crown Vault");
            put(101, "Oasis");
            put(102, "Pizza Sewers");
            put(103, "YV's Mansion");
            put(104, "Cursed Crystal Caves");
            put(105, "Jungle");
            put(107, "YV's Crib");
        }
    };

    public static final Map<Integer, List<String>> WORLD_TIPS = new LinkedHashMap<Integer, List<String>>() {
        {
            put(0, asList(
                "This can't be true",
                "Your friends were here",
                "It's so dark"
            ));
            put(1, asList(
                "The wind hurts",
                "Dust surrounds you",
                "Let's do this",
                "Watch out for maggots",
                "Scorching sun",
                "The wasteland calls you",
                "Welcome to the future"
            ));
            put(2, asList(
                "So many rats",
                "Sludge everywhere",
                "Water dripping",
                "Danger",
                "Don't eat the rat meat",
                "Don't touch the frogs",
                "The sewers stink",
                "Don't drink the water"
            ));
            put(3, asList(
                "Portals can blow up cars",
                "Climb over cars",
                "Sludge pools",
                "The sound of birds",
                "Rust everywhere",
                "Look up",
                "There used to be trees here",
                "Shoot robots on sight"));
            put(4, asList(
                "Almost halfway there",
                "Skin is crawling",
                "Reflections on the walls",
                "Don't lose your heart",
                "Spiderwebs everywhere",
                "Oh no"
            ));
            put(5, asList(
                "There is no yeti",
                "Walk softly",
                "Civilization",
                "Miss the sun",
                "They used to have electricity",
                "Wear a scarf"
            ));
            put(6, asList(
                "Don't push any buttons",
                "Nerds",
                "Beep boop"
            ));
            put(7, asList(
                "The Palace",
                "This place is old"
            ));
            put(100, Collections.singletonList("Awww yes"));
            put(101, asList(
                "Don't move",
                "It's beautiful down here",
                "Hold your breath",
                "Fish"
            ));
            put(102, asList(
                "It smells nice here",
                "Hunger..."
            ));
            put(103, asList(
                "4 years later...",
                "So much money",
                "Always wanted to go here",
                "Space..."
            ));
            put(104, asList(
                "There halfway almost",
                "Crawling is skin",
                "Everywhere spiderwebs",
                "No oh"
            ));
            put(105, asList(
                "Heart of darkness",
                "Welcome to the jungle",
                "Bugs everywhere",
                "There's something in the trees"
            ));
            put(107, asList(
                "Wakkala wayo",
                "Now this is real special",
                "Get the hell out of here",
                "Lets take a look in the fridge",
                "This is where the magic happens"
            ));
        }
    };

    public static final Map<String, String> WEAPON_TIPS = new LinkedHashMap<String, String>() {
        {
            put("Revolver", "Trusty old revolver");
            put("Rusty Revolver", "This Revolver is ancient");
            //put("Machinegun", "");
            put("Triple Machinegun", "Triple Machinegun, so much fun");
            put("Quadruple Machinegun", "The future is here");
            put("Smart Gun", "It thinks");
            //put("SMG", "");
            put("Minigun", "Time to rain bullets");
            put("Double Minigun", "Sea of bullets");
            //put("Assault Rifle", "");
            put("Rogue Rifle", "Loose cannon");
            put("Hyper Rifle", "Hyper time");
            put("Heavy Revolver", "Heavy bullets");
            put("Heavy Machinegun", "Get some");
            put("Heavy Assault Rifle", "Go for it");
            put("Pop Gun", "How does this thing even work");
            put("Pop Rifle", "Let's hope this works");
            //put("Incinerator", ". . .");
            put("Bouncer SMG", "Watch your back");
            put("Bouncer Shotgun", "They're everywhere");
            //put("Shotgun", "");
            put("Double Shotgun", "Double Shotgun, double fun");
            put("Sawed-off Shotgun", "A true melee weapon");
            put("Wave Gun", "Shoot 'em up");
            put("Eraser", "Goodbye head");
            put("Auto Shotgun", "Just hold down the trigger");
            put("Flak Cannon", "10/10");
            put("Super Flak Cannon", "11/10");
            put("Flame Shotgun", "A burning sensation");
            put("Double Flame Shotgun", "Incinerate them");
            put("Auto Flame Shotgun", "Raining fire");
            //put("Slugger", "");
            //put("Assault Slugger", "");
            put("Gatling Slugger", "Time to gatle");
            put("Super Slugger", "No need to aim");
            put("Hyper Slugger", "Time to hyper");
            put("Heavy Slugger", "Get out of here");
            //put("Crossbow", "");
            put("Auto Crossbow", "225 bolts per minute");
            put("Super Crossbow", "5 bolts per shot");
            put("Toxic Bow", "Hold breath while firing");
            put("Heavy Crossbow", "A true burden");
            put("Heavy Auto Crossbow", "Weighs you down");
            put("Splinter Gun", "This will hurt");
            put("Splinter Pistol", "Watch your fingers");
            put("Super Splinter Gun", "Terror");
            put("Seeker Pistol", "No hiding");
            put("Seeker Shotgun", "No escape");
            put("Disc Gun", "We hereby sincerely apologize");
            put("Grenade Launcher", "Be careful with those grenades");
            put("Sticky Launcher", "Don't touch sticky 'nades'");
            put("Toxic Launcher", "Don't breathe'");
            put("Hyper Launcher", "Point and click");
            put("Heavy Grenade Launcher", "Watch out");
            put("Grenade Rifle", "Distant explosions");
            put("Grenade Shotgun", "Don't get too close");
            put("Auto Grenade Shotgun", "Stay away");
            put("Cluster Launcher", "Small explosions");
            //put("Bazooka", "");
            put("Gatling Bazooka", "Explosions");
            put("Super Bazooka", "This is getting silly");
            put("Nuke Launcher", "This is what started it all");
            put("Blood Launcher", "Built with spare parts");
            put("Blood Cannon", "Fully organic");
            put("Flamethrower", "Burn burn burn");
            put("Dragon", "Hot breath");
            put("Flare Gun", "Signal for help");
            put("Flame Cannon", "Inferno");
            put("Laser Pistol", "Futuristic weaponry");
            //put("Laser Rifle", "");
            put("Laser Minigun", "Energy bill off the charts");
            put("Laser Cannon", "Oh Laser Cannon");
            put("Plasma Gun", "Fun fun");
            put("Plasma Rifle", "Fun fun fun fun");
            put("Plasma Minigun", "All the fun");
            put("Plasma Cannon", "Fun fun fun");
            put("Super Plasma Cannon", "Comedy");
            //put("Devastator", ". . .");
            put("Lightning Pistol", "Thunder");
            put("Lighting Rifle", "A storm is coming");
            put("Lightning SMG", "Heavy weather");
            put("Lightning Shotgun", "Hurricane");
            put("Lightning Cannon", "Typhoon");
            put("Screwdriver", "Screwdriver will fix it");
            put("Chicken Sword", "Chicken loves her sword");
            put("Wrench", "Hell");
            put("Shovel", "Dig");
            put("Sledgehammer", "Steel on steel");
            put("Blood Hammer", "Drip");
            put("Lightning Hammer", "Shock value");
            put("Jackhammer", "Break some legs");
            put("Energy Screwdriver", "Future fixing");
            put("Energy Sword", "Zzzwwoonggg");
            put("Energy Hammer", "Break a leg");
            put("Ultra Revolver", "Feeling ultra");
            put("Ultra Shotgun", "No chance");
            put("Ultra Crossbow", "Nowhere to hide");
            put("Ultra Grenade Launcher", "They'll come");
            put("Ultra Laser Pistol", "Unstoppable");
            put("Ultra Shovel", "Perfection");
            //put("Golden Revolver", "B-)");
            put("Golden Machinegun", "Expensive Machinegun");
            put("Golden Assault Rifle", "Burst of gold");
            put("Golden Shotgun", "Beautiful Shotgun");
            put("Golden Slugger", "Priceless hardwood");
            put("Golden Crossbow", "Velvet handles");
            put("Golden Splinter Gun", "Even the ammo is expensive");
            put("Golden Grenade Launcher", "Even the grenades are gold");
            put("Golden Bazooka", "Worth its weight in gold");
            put("Golden Laser Pistol", "This thing gets hot");
            put("Golden Plasma Gun", "Beautiful alloys");
            put("Golden Wrench", "Shiny wrench");
            put("Golden Screwdriver", "Ivory handle");
            put("Frog Pistol", "Always");
            put("Super Disc Gun", "Many apologies");
            put("Gun Gun", "Make it");
            put("Guitar", "There's no reason to fight");
            put("Black Sword", "Chicken fears her sword");
            put("Golden Frog Pistol", "Always believe in your soul");
            put("Golden Disc Gun", "Go in style");
            put("Golden Nuke Launcher", "Excessive");
        }
    };

    public static final Map<String, String> MUTATION_TIPS = new LinkedHashMap<String, String>() {
        {
            put("Back Muscle", "Great strength");
            put("Bloodlust", "Drink blood");
            put("Boiling Veins", "Temperature is rising");
            put("Bolt Marrow", "Bolts everywhere");
            put("Eagle Eyes", "Every shot connects");
            put("Euphoria", "Time passes slowly");
            put("Extra Feet", "Run forever");
            put("Gamma Guts", "Skin glows");
            put("Hammerhead", "Such a headache");
            put("Heavy Heart", "These guns");
            put("Impact Wrists", "See them fly");
            put("Laser Brain", "Neurons everywhere");
            put("Last Wish", "Listen");
            put("Long Arms", "More reach");
            put("Lucky Shot", "Ammo everywhere");
            put("Open Mind", "The truth is out there");
            put("Patience", "Wait a second");
            put("Plutonium Hunger", "Need those Rads");
            put("Rabbit Paw", "Feeling lucky");
            put("Recycle Gland", "Return");
            put("Rhino Skin", "Thick skin");
            put("Scarier Face", "Mirrors will break");
            put("Second Stomach", "Stomach rumbles");
            put("Sharp Teeth", "Eye for an eye");
            put("Shotgun Shoulders", "Shells are friends");
            put("Stress", "Shaking");
            put("Strong Spirit", "Believe in yourself");
            put("Throne Butt", "Sit on the Throne");
            put("Trigger Fingers", "Good idea");
        }
    };

    public static final List<String> HEALED_RESPONSES = asList(
        "",
        "",
        "Oh yeah",
        "Nice",
        "Great",
        "Fantastic",
        "Awesome",
        "Thanks for the health",
        "Finally some health",
        "That hit the spot"
    );

    public static final List<String> HURT_RESPONSES = asList(
        "Got hit by {{an_enemy}}",
        "This motherfucking {{enemy}}",
        "Motherfucker {{enemy}} bit me",
        "I've had it with these {{enemy}}s in this motherfucking level"
    );

    public static final List<String> VAULT_RESPONSES = asList(
        "Crown Vault ain't no country I've ever heard of. They speak English in Vaults?",
        "I've had it with these motherfucking vaults in this motherfucking game."
    );

    public static final List<String> DEATH_RESPONSES = asList(
        "Died to {{enemy}}",
        "Fuck you",
        "Come on",
        "Wake the fuck up",
        "Oh I'm sorry did I break your concentration",
        "See I told you you should've killed that bitch",
        "I don't remember asking you a god damn thing"
    );
}
