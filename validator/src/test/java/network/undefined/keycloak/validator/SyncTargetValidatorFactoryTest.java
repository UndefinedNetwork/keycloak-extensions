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
import org.keycloak.validate.ValidationContext;
import org.keycloak.validate.ValidationError;
import org.keycloak.validate.ValidatorConfig;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncTargetValidatorFactoryTest {

    private static final String CURRENT_USER_ID = "current-user-id";
    private static final String OTHER_USER_ID = "other-user-id";

    private SyncTargetValidatorFactory validator;

    @Mock
    private ValidationContext context;

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
    private UserModel otherUser;

    @Mock
    private UserProfileAttributeValidationContext upContext;

    @BeforeEach
    void setUp() {
        validator = new SyncTargetValidatorFactory();

        lenient().when(context.getSession()).thenReturn(session);
        lenient().when(session.getContext()).thenReturn(keycloakContext);
        lenient().when(keycloakContext.getRealm()).thenReturn(realm);
        lenient().when(session.users()).thenReturn(userProvider);

        lenient().when(upContext.getAttributeContext()).thenReturn(attributeContext);
        lenient().when(attributeContext.getUser()).thenReturn(currentUser);
        lenient().when(currentUser.getId()).thenReturn(CURRENT_USER_ID);
        lenient().when(otherUser.getId()).thenReturn(OTHER_USER_ID);
    }

    private ValidatorConfig buildConfig(String targetField, String transformation) {
        return ValidatorConfig.builder()
                .config(SyncTargetValidatorFactory.CFG_TARGET_FIELD, targetField)
                .config(SyncTargetValidatorFactory.CFG_TRANSFORMATION, transformation)
                .build();
    }

    private ValidatorConfig buildConfig(String targetField, String transformation, String errorMessage) {
        return ValidatorConfig.builder()
                .config(SyncTargetValidatorFactory.CFG_TARGET_FIELD, targetField)
                .config(SyncTargetValidatorFactory.CFG_TRANSFORMATION, transformation)
                .config(SyncTargetValidatorFactory.CFG_ERROR_MESSAGE, errorMessage)
                .build();
    }

    private void runValidation(String value, String inputHint, ValidatorConfig config) {
        try (MockedStatic<UserProfileAttributeValidationContext> mocked =
                     mockStatic(UserProfileAttributeValidationContext.class)) {
            mocked.when(() -> UserProfileAttributeValidationContext.from(context)).thenReturn(upContext);
            validator.validate(value, inputHint, context, config);
        }
    }

    // --- Username target ---

    @Test
    void noConflictUsername() {
        when(userProvider.getUserByUsername(realm, "testvalue")).thenReturn(null);

        runValidation("TestValue", "display_name", buildConfig("username", "lowercase"));

        verify(context, never()).addError(any());
    }

    @Test
    void conflictUsername() {
        when(userProvider.getUserByUsername(realm, "testvalue")).thenReturn(otherUser);

        runValidation("TestValue", "display_name", buildConfig("username", "lowercase"));

        verify(context).addError(any(ValidationError.class));
    }

    @Test
    void sameUserUsername() {
        when(userProvider.getUserByUsername(realm, "testvalue")).thenReturn(currentUser);

        runValidation("TestValue", "display_name", buildConfig("username", "lowercase"));

        verify(context, never()).addError(any());
    }

    // --- Email target ---

    @Test
    void noConflictEmail() {
        when(userProvider.getUserByEmail(realm, "testvalue")).thenReturn(null);

        runValidation("TestValue", "display_name", buildConfig("email", "lowercase"));

        verify(context, never()).addError(any());
    }

    @Test
    void conflictEmail() {
        when(userProvider.getUserByEmail(realm, "testvalue")).thenReturn(otherUser);

        runValidation("TestValue", "display_name", buildConfig("email", "lowercase"));

        verify(context).addError(any(ValidationError.class));
    }

    @Test
    void sameUserEmail() {
        when(userProvider.getUserByEmail(realm, "testvalue")).thenReturn(currentUser);

        runValidation("TestValue", "display_name", buildConfig("email", "lowercase"));

        verify(context, never()).addError(any());
    }

    // --- firstName / lastName (no uniqueness check) ---

    @Test
    void firstNameNoOp() {
        runValidation("TestValue", "display_name", buildConfig("firstName", "lowercase"));

        verify(context, never()).addError(any());
    }

    @Test
    void lastNameNoOp() {
        runValidation("TestValue", "display_name", buildConfig("lastName", "lowercase"));

        verify(context, never()).addError(any());
    }

    // --- Custom attribute target ---

    @Test
    void conflictCustomAttribute() {
        when(userProvider.searchForUserByUserAttributeStream(realm, "custom_attr", "testvalue"))
                .thenReturn(Stream.of(otherUser));

        runValidation("TestValue", "display_name", buildConfig("custom_attr", "lowercase"));

        verify(context).addError(any(ValidationError.class));
    }

    @Test
    void noConflictCustomAttribute() {
        when(userProvider.searchForUserByUserAttributeStream(realm, "custom_attr", "testvalue"))
                .thenReturn(Stream.empty());

        runValidation("TestValue", "display_name", buildConfig("custom_attr", "lowercase"));

        verify(context, never()).addError(any());
    }

    // --- Transformation ---

    @Test
    void transformationLowercase() {
        when(userProvider.getUserByUsername(realm, "testvalue")).thenReturn(null);

        runValidation("TestValue", "display_name", buildConfig("username", "lowercase"));

        verify(userProvider).getUserByUsername(realm, "testvalue");
    }

    @Test
    void transformationNone() {
        when(userProvider.getUserByUsername(realm, "TestValue")).thenReturn(null);

        runValidation("TestValue", "display_name", buildConfig("username", "none"));

        verify(userProvider).getUserByUsername(realm, "TestValue");
    }

    // --- Custom error message ---

    @Test
    void customErrorMessage() {
        when(userProvider.getUserByUsername(realm, "testvalue")).thenReturn(otherUser);

        runValidation("TestValue", "display_name", buildConfig("username", "lowercase", "custom-error"));

        verify(context).addError(any(ValidationError.class));
    }

    // --- Null session / realm guards ---

    @Test
    void nullSession() {
        when(context.getSession()).thenReturn(null);

        try (MockedStatic<UserProfileAttributeValidationContext> mocked =
                     mockStatic(UserProfileAttributeValidationContext.class)) {
            mocked.when(() -> UserProfileAttributeValidationContext.from(context)).thenReturn(upContext);
            validator.validate("TestValue", "display_name", context, buildConfig("username", "lowercase"));
        }

        verify(context, never()).addError(any());
    }

    @Test
    void nullRealm() {
        when(keycloakContext.getRealm()).thenReturn(null);

        try (MockedStatic<UserProfileAttributeValidationContext> mocked =
                     mockStatic(UserProfileAttributeValidationContext.class)) {
            mocked.when(() -> UserProfileAttributeValidationContext.from(context)).thenReturn(upContext);
            validator.validate("TestValue", "display_name", context, buildConfig("username", "lowercase"));
        }

        verify(context, never()).addError(any());
    }

    // --- Provider metadata ---

    @Test
    void providerIdAndConfig() {
        assertEquals("sync-target-validator", validator.getId());
        assertEquals(3, validator.getConfigProperties().size());
        assertFalse(validator.getHelpText().isEmpty());
    }
}
