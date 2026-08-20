package dev.dreamcraft.protection.domain.model;

/**
 * Governance policy flags for a City.
 */
public enum CityPolicy {
    /** Wards owned by members are automatically associated to this City when created nearby. */
    AUTO_ASSOCIATE_WARDS,
    /** Members can invite other players without Governor approval. */
    OPEN_RECRUITMENT,
    /** Citizens can create Wards without Governor approval. */
    FREE_WARD_CREATION,
    /** Treasury withdrawals require council vote. */
    COUNCIL_TREASURY_APPROVAL,
    /** City is visible in public listings. */
    PUBLIC_LISTING
}
