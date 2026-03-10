package network.undefined.keycloak.validator;

import org.keycloak.Config;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.List;

public class AttributeSyncListenerFactory implements EventListenerProviderFactory {

    public static final String PROVIDER_ID = "attribute-sync";

    private static final String DEFAULT_SOURCE_ATTRIBUTE = "display_name";
    private static final String DEFAULT_TARGET_FIELD = "username";
    private static final String DEFAULT_TRANSFORMATION = "lowercase";

    private String sourceAttribute;
    private String targetField;
    private String transformation;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new AttributeSyncListener(session, sourceAttribute, targetField, transformation);
    }

    @Override
    public void init(Config.Scope config) {
        sourceAttribute = config.get("sourceAttribute", DEFAULT_SOURCE_ATTRIBUTE);
        targetField = config.get("targetField", DEFAULT_TARGET_FIELD);
        transformation = config.get("transformation", DEFAULT_TRANSFORMATION);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    private static class AttributeSyncListener implements EventListenerProvider {

        private final KeycloakSession session;
        private final String sourceAttribute;
        private final String targetField;
        private final String transformation;

        AttributeSyncListener(KeycloakSession session, String sourceAttribute,
                              String targetField, String transformation) {
            this.session = session;
            this.sourceAttribute = sourceAttribute;
            this.targetField = targetField;
            this.transformation = transformation;
        }

        @Override
        public void onEvent(Event event) {
            if (event.getType() == EventType.REGISTER
                    || event.getType() == EventType.UPDATE_PROFILE) {
                syncField(event.getRealmId(), event.getUserId());
            }
        }

        @Override
        public void onEvent(AdminEvent event, boolean includeRepresentation) {
            if (event.getResourceType() == ResourceType.USER
                    && event.getOperationType() == OperationType.UPDATE) {
                String path = event.getResourcePath();
                // resourcePath is "users/<user-id>"
                if (path != null && path.startsWith("users/")) {
                    String userId = path.substring("users/".length());
                    syncField(event.getRealmId(), userId);
                }
            }
        }

        private void syncField(String realmId, String userId) {
            RealmModel realm = session.realms().getRealm(realmId);
            if (realm == null) return;

            UserModel user = session.users().getUserById(realm, userId);
            if (user == null) return;

            List<String> values = user.getAttributes().get(sourceAttribute);
            if (values == null || values.isEmpty()) return;

            String sourceValue = values.get(0);
            String desired = "lowercase".equals(transformation)
                    ? sourceValue.toLowerCase() : sourceValue;

            switch (targetField) {
                case "username":
                    if (!desired.equals(user.getUsername())) {
                        user.setUsername(desired);
                    }
                    break;
                case "firstName":
                    if (!desired.equals(user.getFirstName())) {
                        user.setFirstName(desired);
                    }
                    break;
                case "lastName":
                    if (!desired.equals(user.getLastName())) {
                        user.setLastName(desired);
                    }
                    break;
                case "email":
                    if (!desired.equals(user.getEmail())) {
                        user.setEmail(desired);
                    }
                    break;
                default:
                    // target is another user attribute
                    List<String> current = user.getAttributes().get(targetField);
                    if (current == null || current.isEmpty() || !desired.equals(current.get(0))) {
                        user.setSingleAttribute(targetField, desired);
                    }
                    break;
            }
        }

        @Override
        public void close() {
        }
    }
}
