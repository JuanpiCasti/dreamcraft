package dev.dreamcraft.protection.domain.model;

/**
 * Discriminates who owns a Ward: a single player or a City.
 */
public enum OwnerType {
    PLAYER,
    CITY
}
