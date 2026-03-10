package network.undefined.keycloak.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.userprofile.AttributeContext;
import org.keycloak.userprofile.UserProfileAttributeValidationContext;
import org.keycloak.validate.ValidationError;
import org.keycloak.validate.ValidatorConfig;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UniqueAttributeValidatorFactoryTest {

    private static final String ATTRIBUTE = "display_name";

    private final UniqueAttributeValidatorFactory validator = new UniqueAttributeValidatorFactory();

    @Mock
    private UserProfileAttributeValidationContext context;

    @Mock
    private AttributeContext attributeContext;

    @Mock
    private KeycloakSession session;

    @Mock
    private KeycloakContext keycloakContext;

    @Mock
    private RealmModel realm;

    @Mock
    private UserProvider userProvider;

    @Mock
    private UserModel currentUser;

    @Mock
    private ValidatorConfig config;

    @Captor
    private ArgumentCaptor<ValidationError> errorCaptor;

    @BeforeEach
    void setUp() {
        lenient().when(context.getSession()).thenReturn(session);
        lenient().when(session.getContext()).thenReturn(keycloakContext);
        lenient().when(keycloakContext.getRealm()).thenReturn(realm);
        lenient().when(session.users()).thenReturn(userProvider);
        lenient().when(context.getAttributeContext()).thenReturn(attributeContext);
        lenient().when(attributeContext.getUser()).thenReturn(currentUser);
        lenient().when(currentUser.getId()).thenReturn("current-user-id");
    }

    private void configureAttributeMode() {
        when(config.containsKey("error-message")).thenReturn(false);
        when(config.getStringOrDefault("lookup-by-username", "false")).thenReturn("false");
    }

    private void configureUsernameMode() {
        when(config.containsKey("error-message")).thenReturn(false);
        when(config.getStringOrDefault("lookup-by-username", "false")).thenReturn("true");
    }

    private void runValidation(String value) {
        try (MockedStatic<UserProfileAttributeValidationContext> mocked =
                     mockStatic(UserProfileAttributeValidationContext.class)) {
            mocked.when(() -> UserProfileAttributeValidationContext.from(context)).thenReturn(context);
            validator.validate(value, ATTRIBUTE, context, config);
        }
    }

    @Test
    void uniqueByAttribute() {
        configureAttributeMode();
        when(userProvider.searchForUserByUserAttributeStream(realm, ATTRIBUTE, "UniqueVal"))
                .thenReturn(Stream.empty());

        runValidation("UniqueVal");

        verify(context, never()).addError(any());
    }

    @Test
    void duplicateByAttribute() {
        configureAttributeMode();
        UserModel otherUser = mock(UserModel.class);
        when(otherUser.getId()).thenReturn("other-user-id");
        when(userProvider.searchForUserByUserAttributeStream(realm, ATTRIBUTE, "DupeVal"))
                .thenReturn(Stream.of(otherUser));

        runValidation("DupeVal");

        verify(context).addError(any());
    }

    @Test
    void sameUserByAttribute() {
        configureAttributeMode();
        UserModel sameUser = mock(UserModel.class);
        when(sameUser.getId()).thenReturn("current-user-id");
        when(userProvider.searchForUserByUserAttributeStream(realm, ATTRIBUTE, "MyVal"))
                .thenReturn(Stream.of(sameUser));

        runValidation("MyVal");

        verify(context, never()).addError(any());
    }

    @Test
    void uniqueByUsername() {
        configureUsernameMode();
        when(userProvider.getUserByUsername(realm, "testvalue")).thenReturn(null);

        runValidation("TestValue");

        verify(context, never()).addError(any());
    }

    @Test
    void duplicateByUsername() {
        configureUsernameMode();
        UserModel existing = mock(UserModel.class);
        when(existing.getId()).thenReturn("other-user-id");
        when(existing.getAttributes()).thenReturn(Map.of(ATTRIBUTE, List.of("TestValue")));
        when(userProvider.getUserByUsername(realm, "testvalue")).thenReturn(existing);

        runValidation("TestValue");

        verify(context).addError(any());
    }

    @Test
    void sameUserByUsername() {
        configureUsernameMode();
        UserModel existing = mock(UserModel.class);
        when(existing.getId()).thenReturn("current-user-id");
        when(userProvider.getUserByUsername(realm, "testvalue")).thenReturn(existing);

        runValidation("TestValue");

        verify(context, never()).addError(any());
    }

    @Test
    void duplicateByUsernameWrongAttribute() {
        configureUsernameMode();
        UserModel existing = mock(UserModel.class);
        when(existing.getId()).thenReturn("other-user-id");
        when(existing.getAttributes()).thenReturn(Map.of(ATTRIBUTE, List.of("DifferentValue")));
        when(userProvider.getUserByUsername(realm, "testvalue")).thenReturn(existing);

        runValidation("TestValue");

        verify(context, never()).addError(any());
    }

    @Test
    void customErrorMessage() {
        when(config.containsKey("error-message")).thenReturn(true);
        when(config.getString("error-message")).thenReturn("custom-msg");
        when(config.getStringOrDefault("lookup-by-username", "false")).thenReturn("false");

        UserModel otherUser = mock(UserModel.class);
        when(otherUser.getId()).thenReturn("other-user-id");
        when(userProvider.searchForUserByUserAttributeStream(realm, ATTRIBUTE, "DupeVal"))
                .thenReturn(Stream.of(otherUser));

        runValidation("DupeVal");

        verify(context).addError(errorCaptor.capture());
        assertEquals("custom-msg", errorCaptor.getValue().getMessage());
    }

    @Test
    void defaultErrorMessage() {
        configureAttributeMode();

        UserModel otherUser = mock(UserModel.class);
        when(otherUser.getId()).thenReturn("other-user-id");
        when(userProvider.searchForUserByUserAttributeStream(realm, ATTRIBUTE, "DupeVal"))
                .thenReturn(Stream.of(otherUser));

        runValidation("DupeVal");

        verify(context).addError(errorCaptor.capture());
        assertEquals("error-attribute-not-unique", errorCaptor.getValue().getMessage());
    }

    @Test
    void nullSession() {
        when(context.getSession()).thenReturn(null);

        try (MockedStatic<UserProfileAttributeValidationContext> mocked =
                     mockStatic(UserProfileAttributeValidationContext.class)) {
            mocked.when(() -> UserProfileAttributeValidationContext.from(context)).thenReturn(context);
            validator.validate("AnyValue", ATTRIBUTE, context, config);
        }

        verify(context, never()).addError(any());
    }

    @Test
    void nullRealm() {
        when(keycloakContext.getRealm()).thenReturn(null);

        try (MockedStatic<UserProfileAttributeValidationContext> mocked =
                     mockStatic(UserProfileAttributeValidationContext.class)) {
            mocked.when(() -> UserProfileAttributeValidationContext.from(context)).thenReturn(context);
            validator.validate("AnyValue", ATTRIBUTE, context, config);
        }

        verify(context, never()).addError(any());
    }

    @Test
    void providerIdAndConfig() {
        assertEquals("unique-attribute", validator.getId());
        assertEquals(2, validator.getConfigProperties().size());
    }
}
