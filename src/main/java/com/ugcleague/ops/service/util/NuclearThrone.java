package com.ugcleague.ops.service.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;

public class NuclearThrone {

    /**
     * Characters are 0-indexed
     **/
    public static List<String> CHARACTERS = asList(
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

    /**
     * Ultras are 1x1-indexed (character, index)
     **/
    public static List<List<String>> ULTRAS = asList(
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

    /**
     * Mutations are 0-indexed
     **/
    public static List<String> MUTATIONS = asList(
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
    public static List<String> CROWNS = asList(
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

    /**
     * Weapons are 0-indexed
     */
    public static List<String> WEAPONS = asList("Nothing",
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

    public static Map<Integer, String> ENEMIES = new LinkedHashMap<Integer, String>() {
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

    public static Map<Integer, String> WORLDS = new LinkedHashMap<Integer, String>() {
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
}
