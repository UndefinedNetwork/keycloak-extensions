package network.undefined.keycloak.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.Config;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttributeSyncListenerFactoryTest {

    private static final String REALM_ID = "realm1";
    private static final String USER_ID = "user123";

    @Mock
    private KeycloakSession session;

    @Mock
    private RealmProvider realmProvider;

    @Mock
    private UserProvider userProvider;

    @Mock
    private RealmModel realm;

    @Mock
    private UserModel user;

    @BeforeEach
    void setUp() {
        lenient().when(session.realms()).thenReturn(realmProvider);
        lenient().when(session.users()).thenReturn(userProvider);
        lenient().when(realmProvider.getRealm(REALM_ID)).thenReturn(realm);
        lenient().when(userProvider.getUserById(realm, USER_ID)).thenReturn(user);
        lenient().when(user.getAttributes()).thenReturn(Map.of("display_name", List.of("TestValue")));
    }

    private AttributeSyncListenerFactory createFactory(String sourceAttr, String targetField, String transformation) {
        return createFactory(sourceAttr, targetField, transformation, null, null, "none");
    }

    private AttributeSyncListenerFactory createFactory(String sourceAttr, String targetField, String transformation,
                                                        String onRegSourceField, String onRegTargetAttr, String onRegTransformation) {
        AttributeSyncListenerFactory factory = new AttributeSyncListenerFactory();
        Config.Scope scope = org.mockito.Mockito.mock(Config.Scope.class);
        when(scope.get("sourceAttribute", "display_name")).thenReturn(sourceAttr);
        when(scope.get("targetField", "username")).thenReturn(targetField);
        when(scope.get("transformation", "lowercase")).thenReturn(transformation);
        when(scope.get("onRegisterSourceField")).thenReturn(onRegSourceField);
        when(scope.get("onRegisterTargetAttribute")).thenReturn(onRegTargetAttr);
        when(scope.get("onRegisterTransformation", "none")).thenReturn(onRegTransformation);
        factory.init(scope);
        return factory;
    }

    private Event createEvent(EventType type) {
        return createEvent(type, null);
    }

    private Event createEvent(EventType type, Map<String, String> details) {
        Event event = new Event();
        event.setType(type);
        event.setRealmId(REALM_ID);
        event.setUserId(USER_ID);
        if (details != null) {
            event.setDetails(details);
        }
        return event;
    }

    private AdminEvent createAdminEvent(ResourceType resourceType, OperationType operationType, String resourcePath) {
        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setResourceType(resourceType);
        adminEvent.setOperationType(operationType);
        adminEvent.setRealmId(REALM_ID);
        adminEvent.setResourcePath(resourcePath);
        return adminEvent;
    }

    // ---- Existing sync tests ----

    @Test
    void syncUsernameOnRegister() {
        AttributeSyncListenerFactory factory = createFactory("display_name", "username", "lowercase");
        EventListenerProvider listener = factory.create(session);

        listener.onEvent(createEvent(EventType.REGISTER));

        verify(user).setUsername("testvalue");
    }

    @Test
    void syncUsernameOnUpdateProfile() {
        AttributeSyncListenerFactory factory = createFactory("display_name", "username", "lowercase");
        EventListenerProvider listener = factory.create(session);

        listener.onEvent(createEvent(EventType.UPDATE_PROFILE));

        verify(user).setUsername("testvalue");
    }

    @Test
    void syncUsernameOnAdminUpdate() {
        AttributeSyncListenerFactory factory = createFactory("display_name", "username", "lowercase");
        EventListenerProvider listener = factory.create(session);

        AdminEvent adminEvent = createAdminEvent(ResourceType.USER, OperationType.UPDATE, "users/" + USER_ID);
        listener.onEvent(adminEvent, false);

        verify(user).setUsername("testvalue");
    }

    @Test
    void ignoredEventType() {
        AttributeSyncListenerFactory factory = createFactory("display_name", "username", "lowercase");
        EventListenerProvider listener = factory.create(session);

        listener.onEvent(createEvent(EventType.LOGIN));

        verify(user, never()).setUsername(any());
    }

    @Test
    void ignoredAdminResource() {
        AttributeSyncListenerFactory factory = createFactory("display_name", "username", "lowercase");
        EventListenerProvider listener = factory.create(session);

        AdminEvent adminEvent = createAdminEvent(ResourceType.GROUP, OperationType.UPDATE, "groups/group1");
        listener.onEvent(adminEvent, false);

        verify(user, never()).setUsername(any());
    }

    @Test
    void syncEmail() {
        AttributeSyncListenerFactory factory = createFactory("display_name", "email", "lowercase");
        EventListenerProvider listener = factory.create(session);

        listener.onEvent(createEvent(EventType.REGISTER));

        verify(user).setEmail("testvalue");
    }

    @Test
    void syncFirstName() {
        AttributeSyncListenerFactory factory = createFactory("display_name", "firstName", "lowercase");
        EventListenerProvider listener = factory.create(session);

        listener.onEvent(createEvent(EventType.REGISTER));

        verify(user).setFirstName("testvalue");
    }

    @Test
    void syncLastName() {
        AttributeSyncListenerFactory factory = createFactory("display_name", "lastName", "lowercase");
        EventListenerProvider listener = factory.create(session);

        listener.onEvent(createEvent(EventType.REGISTER));

        verify(user).setLastName("testvalue");
    }

    @Test
    void syncCustomAttribute() {
        AttributeSyncListenerFactory factory = createFactory("display_name", "custom_attr", "lowercase");
        EventListenerProvider listener = factory.create(session);

        lenient().when(user.getAttributes()).thenReturn(Map.of("display_name", List.of("TestValue")));
        listener.onEvent(createEvent(EventType.REGISTER));

        verify(user).setSingleAttribute("custom_attr", "testvalue");
    }

    @Test
    void transformationNone() {
        AttributeSyncListenerFactory factory = createFactory("display_name", "username", "none");
        EventListenerProvider listener = factory.create(session);

        listener.onEvent(createEvent(EventType.REGISTER));

        verify(user).setUsername("TestValue");
    }

    @Test
    void transformationLowercase() {
        AttributeSyncListenerFactory factory = createFactory("display_name", "username", "lowercase");
        EventListenerProvider listener = factory.create(session);

        listener.onEvent(createEvent(EventType.REGISTER));

        verify(user).setUsername("testvalue");
    }

    @Test
    void noSourceAttribute() {
        when(user.getAttributes()).thenReturn(Collections.emptyMap());

        AttributeSyncListenerFactory factory = createFactory("display_name", "username", "lowercase");
        EventListenerProvider listener = factory.create(session);

        listener.onEvent(createEvent(EventType.REGISTER));

        verify(user, never()).setUsername(any());
    }

    @Test
    void nullUser() {
        when(userProvider.getUserById(realm, USER_ID)).thenReturn(null);

        AttributeSyncListenerFactory factory = createFactory("display_name", "username", "lowercase");
        EventListenerProvider listener = factory.create(session);

        listener.onEvent(createEvent(EventType.REGISTER));

        verify(user, never()).setUsername(any());
    }

    @Test
    void nullRealm() {
        when(realmProvider.getRealm(REALM_ID)).thenReturn(null);

        AttributeSyncListenerFactory factory = createFactory("display_name", "username", "lowercase");
        EventListenerProvider listener = factory.create(session);

        listener.onEvent(createEvent(EventType.REGISTER));

        verify(user, never()).setUsername(any());
    }

    @Test
    void noChangeSkipsSet() {
        when(user.getUsername()).thenReturn("testvalue");

        AttributeSyncListenerFactory factory = createFactory("display_name", "username", "lowercase");
        EventListenerProvider listener = factory.create(session);

        listener.onEvent(createEvent(EventType.REGISTER));

        verify(user, never()).setUsername(any());
    }

    @Test
    void providerIdAndDefaults() {
        AttributeSyncListenerFactory factory = new AttributeSyncListenerFactory();
        assertEquals("attribute-sync", factory.getId());
    }

    // ---- On-register reverse sync tests ----
    // Keycloak lowercases username before the REGISTER event fires, so
    // user.getUsername() returns "johndoe". The original "JohnDoe" is
    // preserved in event.getDetails().get("username").

    @Test
    void onRegisterReadsOriginalCaseFromEventDetails() {
        // user model has lowercased username (KC default behavior)
        lenient().when(user.getUsername()).thenReturn("johndoe");
        when(user.getAttributes()).thenReturn(Collections.emptyMap());

        AttributeSyncListenerFactory factory = createFactory(
                "display_name", "username", "lowercase",
                "username", "display_name", "none");
        EventListenerProvider listener = factory.create(session);

        // event details have the original mixed-case value
        Event event = createEvent(EventType.REGISTER, Map.of("username", "JohnDoe"));
        listener.onEvent(event);

        // display_name gets the original case, not the lowercased one
        verify(user).setSingleAttribute("display_name", "JohnDoe");
    }

    @Test
    void onRegisterFallsBackToUserModelWhenNoDetails() {
        when(user.getUsername()).thenReturn("johndoe");
        when(user.getAttributes()).thenReturn(Collections.emptyMap());

        AttributeSyncListenerFactory factory = createFactory(
                "display_name", "username", "lowercase",
                "username", "display_name", "none");
        EventListenerProvider listener = factory.create(session);

        // no details on event -- falls back to user model
        listener.onEvent(createEvent(EventType.REGISTER));

        verify(user).setSingleAttribute("display_name", "johndoe");
    }

    @Test
    void onRegisterLowercaseTransformation() {
        lenient().when(user.getUsername()).thenReturn("johndoe");
        when(user.getAttributes()).thenReturn(Collections.emptyMap());

        AttributeSyncListenerFactory factory = createFactory(
                "display_name", "username", "lowercase",
                "username", "display_name", "lowercase");
        EventListenerProvider listener = factory.create(session);

        Event event = createEvent(EventType.REGISTER, Map.of("username", "JohnDoe"));
        listener.onEvent(event);

        verify(user).setSingleAttribute("display_name", "johndoe");
    }

    @Test
    void onRegisterSkipsUpdateProfile() {
        AttributeSyncListenerFactory factory = createFactory(
                "display_name", "username", "lowercase",
                "username", "display_name", "none");
        EventListenerProvider listener = factory.create(session);

        listener.onEvent(createEvent(EventType.UPDATE_PROFILE));

        verify(user, never()).setSingleAttribute(any(), any());
    }

    @Test
    void onRegisterSkipsAdminUpdate() {
        AttributeSyncListenerFactory factory = createFactory(
                "display_name", "username", "lowercase",
                "username", "display_name", "none");
        EventListenerProvider listener = factory.create(session);

        AdminEvent adminEvent = createAdminEvent(ResourceType.USER, OperationType.UPDATE, "users/" + USER_ID);
        listener.onEvent(adminEvent, false);

        verify(user, never()).setSingleAttribute(any(), any());
    }

    @Test
    void onRegisterDisabledWhenUnconfigured() {
        AttributeSyncListenerFactory factory = createFactory(
                "display_name", "username", "lowercase",
                null, null, "none");
        EventListenerProvider listener = factory.create(session);

        listener.onEvent(createEvent(EventType.REGISTER));

        // syncField runs but onRegisterSync is a no-op
        verify(user).setUsername("testvalue");
        verify(user, never()).setSingleAttribute(any(), any());
    }

    @Test
    void onRegisterReadsEmailFromEventDetails() {
        when(user.getAttributes()).thenReturn(Collections.emptyMap());

        AttributeSyncListenerFactory factory = createFactory(
                "display_name", "username", "lowercase",
                "email", "display_name", "none");
        EventListenerProvider listener = factory.create(session);

        Event event = createEvent(EventType.REGISTER, Map.of("email", "John@Example.COM"));
        listener.onEvent(event);

        verify(user).setSingleAttribute("display_name", "John@Example.COM");
    }

    @Test
    void onRegisterSkipsNullSource() {
        when(user.getUsername()).thenReturn(null);

        AttributeSyncListenerFactory factory = createFactory(
                "display_name", "username", "lowercase",
                "username", "display_name", "none");
        EventListenerProvider listener = factory.create(session);

        // no "username" in details either
        listener.onEvent(createEvent(EventType.REGISTER));

        verify(user, never()).setSingleAttribute(any(), any());
    }

    @Test
    void onRegisterRunsBeforeSyncField() {
        when(user.getUsername()).thenReturn("johndoe");
        // First call (onRegisterSync): no display_name yet -> triggers setSingleAttribute
        // Second call (syncField): display_name present -> triggers setUsername (no-op since already johndoe)
        when(user.getAttributes())
                .thenReturn(Collections.emptyMap())
                .thenReturn(Map.of("display_name", List.of("JohnDoe")));

        AttributeSyncListenerFactory factory = createFactory(
                "display_name", "username", "lowercase",
                "username", "display_name", "none");
        EventListenerProvider listener = factory.create(session);

        Event event = createEvent(EventType.REGISTER, Map.of("username", "JohnDoe"));
        listener.onEvent(event);

        InOrder order = inOrder(user);
        order.verify(user).setSingleAttribute("display_name", "JohnDoe");
        // syncField reads display_name "JohnDoe", lowercases to "johndoe",
        // but username is already "johndoe" so setUsername is NOT called
    }
}
