package dev.dreamcraft.protection.domain.model;

/**
 * Roles within a City, managed entirely by DreamCraft.
 * LuckPerms is used only for global/administrative permission resolution,
 * not for per-city group creation.
 */
public enum CityRole {
    GOVERNOR,
    COUNCIL,
    CITIZEN,
    ALLY
}
