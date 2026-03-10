package network.undefined.keycloak.validator;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ConfiguredProvider;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.userprofile.AttributeContext;
import org.keycloak.userprofile.UserProfileAttributeValidationContext;
import org.keycloak.validate.AbstractStringValidator;
import org.keycloak.validate.ValidationContext;
import org.keycloak.validate.ValidationError;
import org.keycloak.validate.ValidatorConfig;

import java.util.List;
import java.util.Optional;

/**
 * Generic User Profile validator that ensures an attribute value is unique
 * across all users in the realm.
 *
 * <p>Supports two modes:</p>
 * <ul>
 *   <li><b>Attribute search</b> (default) — queries users by attribute value
 *       using {@code searchForUserByUserAttributeStream}.</li>
 *   <li><b>Username lookup</b> — looks up by username instead. Useful when
 *       paired with an event listener that syncs the attribute to username
 *       (e.g. {@code attribute-sync}), giving case-insensitive uniqueness.</li>
 * </ul>
 *
 * <p>Configuration (set in User Profile attribute validation config):</p>
 * <ul>
 *   <li>{@code error-message} — custom error message key
 *       (default: {@code error-attribute-not-unique})</li>
 *   <li>{@code lookup-by-username} — if {@code true}, check uniqueness via
 *       username lookup on {@code value.toLowerCase()} instead of attribute
 *       search (default: {@code false})</li>
 * </ul>
 */
public class UniqueAttributeValidatorFactory extends AbstractStringValidator implements ConfiguredProvider {

    public static final String PROVIDER_ID = "unique-attribute";

    public static final String CFG_ERROR_MESSAGE = "error-message";
    public static final String CFG_LOOKUP_BY_USERNAME = "lookup-by-username";

    private static final String DEFAULT_ERROR = "error-attribute-not-unique";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "Validates that the attribute value is unique across all users in the realm.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                    .name(CFG_ERROR_MESSAGE)
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .label("Error message key")
                    .helpText("Message key returned on duplicate (default: error-attribute-not-unique)")
                    .defaultValue(DEFAULT_ERROR)
                    .add()
                .property()
                    .name(CFG_LOOKUP_BY_USERNAME)
                    .type(ProviderConfigProperty.BOOLEAN_TYPE)
                    .label("Lookup by username")
                    .helpText("Check uniqueness via username lookup (value.toLowerCase()) instead of attribute search. "
                            + "Useful when paired with an event listener that syncs the attribute to username.")
                    .defaultValue(Boolean.FALSE)
                    .add()
                .build();
    }

    @Override
    protected void doValidate(String value, String inputHint, ValidationContext context, ValidatorConfig config) {
        KeycloakSession session = context.getSession();
        if (session == null) return;

        RealmModel realm = session.getContext().getRealm();
        if (realm == null) return;

        UserModel currentUser = getCurrentUser(context);

        String errorMessage = config != null && config.containsKey(CFG_ERROR_MESSAGE)
                ? config.getString(CFG_ERROR_MESSAGE)
                : DEFAULT_ERROR;

        boolean lookupByUsername = config != null
                && Boolean.parseBoolean(config.getStringOrDefault(CFG_LOOKUP_BY_USERNAME, "false"));

        boolean duplicate;
        if (lookupByUsername) {
            duplicate = isDuplicateByUsername(session, realm, currentUser, value, inputHint);
        } else {
            duplicate = isDuplicateByAttribute(session, realm, currentUser, value, inputHint);
        }

        if (duplicate) {
            context.addError(new ValidationError(PROVIDER_ID, inputHint, errorMessage, value));
        }
    }

    private boolean isDuplicateByAttribute(KeycloakSession session, RealmModel realm,
                                           UserModel currentUser, String value, String attribute) {
        Optional<UserModel> match = session.users()
                .searchForUserByUserAttributeStream(realm, attribute, value)
                .filter(user -> currentUser == null || !user.getId().equals(currentUser.getId()))
                .findFirst();

        return match.isPresent();
    }

    private boolean isDuplicateByUsername(KeycloakSession session, RealmModel realm,
                                         UserModel currentUser, String value, String attribute) {
        String desiredUsername = value.toLowerCase();
        UserModel existing = session.users().getUserByUsername(realm, desiredUsername);

        if (existing == null) return false;
        if (currentUser != null && existing.getId().equals(currentUser.getId())) return false;

        // Verify the collision is from the same attribute
        List<String> attrs = existing.getAttributes().get(attribute);
        return attrs != null && !attrs.isEmpty()
                && attrs.get(0).toLowerCase().equals(desiredUsername);
    }

    private UserModel getCurrentUser(ValidationContext context) {
        UserProfileAttributeValidationContext upContext =
                UserProfileAttributeValidationContext.from(context);
        if (upContext != null) {
            AttributeContext attrCtx = upContext.getAttributeContext();
            if (attrCtx != null) {
                return attrCtx.getUser();
            }
        }
        return null;
    }
}
