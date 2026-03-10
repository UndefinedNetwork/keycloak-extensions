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
 * User Profile validator that checks whether syncing a source attribute to a
 * target field would cause a conflict (e.g. duplicate username).
 *
 * <p>This mirrors the transformation logic of {@link AttributeSyncListenerFactory}
 * and validates <b>before</b> the profile is saved, preventing silent sync failures.</p>
 *
 * <p>Configuration:</p>
 * <ul>
 *   <li>{@code target-field} — target field: {@code username}, {@code email},
 *       {@code firstName}, {@code lastName}, or an attribute name (default: {@code username})</li>
 *   <li>{@code transformation} — {@code lowercase} or {@code none} (default: {@code lowercase})</li>
 *   <li>{@code error-message} — custom error message key
 *       (default: {@code error-sync-target-conflict})</li>
 * </ul>
 */
public class SyncTargetValidatorFactory extends AbstractStringValidator implements ConfiguredProvider {

    public static final String PROVIDER_ID = "sync-target-validator";

    public static final String CFG_TARGET_FIELD = "target-field";
    public static final String CFG_TRANSFORMATION = "transformation";
    public static final String CFG_ERROR_MESSAGE = "error-message";

    private static final String DEFAULT_TARGET_FIELD = "username";
    private static final String DEFAULT_TRANSFORMATION = "lowercase";
    private static final String DEFAULT_ERROR = "error-sync-target-conflict";

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "Validates that syncing this attribute to a target field would not cause a conflict.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                    .name(CFG_TARGET_FIELD)
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .label("Target field")
                    .helpText("The field the sync listener writes to: username, email, firstName, lastName, or an attribute name.")
                    .defaultValue(DEFAULT_TARGET_FIELD)
                    .add()
                .property()
                    .name(CFG_TRANSFORMATION)
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .label("Transformation")
                    .helpText("Transformation applied before syncing: 'lowercase' or 'none'. Must match sync listener config.")
                    .defaultValue(DEFAULT_TRANSFORMATION)
                    .add()
                .property()
                    .name(CFG_ERROR_MESSAGE)
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .label("Error message key")
                    .helpText("Message key returned on conflict (default: error-sync-target-conflict)")
                    .defaultValue(DEFAULT_ERROR)
                    .add()
                .build();
    }

    @Override
    protected void doValidate(String value, String inputHint, ValidationContext context, ValidatorConfig config) {
        KeycloakSession session = context.getSession();
        if (session == null) return;

        RealmModel realm = session.getContext().getRealm();
        if (realm == null) return;

        String targetField = config != null && config.containsKey(CFG_TARGET_FIELD)
                ? config.getString(CFG_TARGET_FIELD)
                : DEFAULT_TARGET_FIELD;

        String transformation = config != null && config.containsKey(CFG_TRANSFORMATION)
                ? config.getString(CFG_TRANSFORMATION)
                : DEFAULT_TRANSFORMATION;

        String errorMessage = config != null && config.containsKey(CFG_ERROR_MESSAGE)
                ? config.getString(CFG_ERROR_MESSAGE)
                : DEFAULT_ERROR;

        String transformed = "lowercase".equals(transformation) ? value.toLowerCase() : value;

        UserModel currentUser = getCurrentUser(context);

        boolean conflict;
        switch (targetField) {
            case "username":
                conflict = isConflictByUsername(session, realm, currentUser, transformed);
                break;
            case "email":
                conflict = isConflictByEmail(session, realm, currentUser, transformed);
                break;
            case "firstName":
            case "lastName":
                // No uniqueness constraints on these fields
                conflict = false;
                break;
            default:
                // Target is another user attribute
                conflict = isConflictByAttribute(session, realm, currentUser, transformed, targetField);
                break;
        }

        if (conflict) {
            context.addError(new ValidationError(PROVIDER_ID, inputHint, errorMessage, targetField));
        }
    }

    private boolean isConflictByUsername(KeycloakSession session, RealmModel realm,
                                        UserModel currentUser, String transformed) {
        UserModel existing = session.users().getUserByUsername(realm, transformed);
        if (existing == null) return false;
        return currentUser == null || !existing.getId().equals(currentUser.getId());
    }

    private boolean isConflictByEmail(KeycloakSession session, RealmModel realm,
                                      UserModel currentUser, String transformed) {
        UserModel existing = session.users().getUserByEmail(realm, transformed);
        if (existing == null) return false;
        return currentUser == null || !existing.getId().equals(currentUser.getId());
    }

    private boolean isConflictByAttribute(KeycloakSession session, RealmModel realm,
                                          UserModel currentUser, String transformed, String attribute) {
        Optional<UserModel> match = session.users()
                .searchForUserByUserAttributeStream(realm, attribute, transformed)
                .filter(user -> currentUser == null || !user.getId().equals(currentUser.getId()))
                .findFirst();
        return match.isPresent();
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
