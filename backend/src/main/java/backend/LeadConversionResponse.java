package backend;

/**
 * Outcome of converting a WON lead into a client.
 *
 * {@code created} is true when a brand new client was created and false when
 * the lead matched an existing client by email (in which case the existing
 * client is linked instead of duplicating it).
 */
public record LeadConversionResponse(
        LeadResponse lead,
        ClientResponse client,
        boolean created) {
}
