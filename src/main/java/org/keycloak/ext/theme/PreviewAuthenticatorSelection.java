package org.keycloak.ext.theme;

import org.keycloak.credential.CredentialTypeMetadata;

/**
 * Stand-in for {@link org.keycloak.authentication.AuthenticationSelectionOption} so
 * {@code select-authenticator.ftl} can list choices without a real authentication flow.
 */
public final class PreviewAuthenticatorSelection {

    private final String authExecId;
    private final String displayName;
    private final String helpText;
    private final String iconCssClass;

    public PreviewAuthenticatorSelection(String authExecId, String displayNameMsgKey, String helpTextMsgKey) {
        this(authExecId, displayNameMsgKey, helpTextMsgKey, CredentialTypeMetadata.DEFAULT_ICON_CSS_CLASS);
    }

    public PreviewAuthenticatorSelection(String authExecId, String displayNameMsgKey, String helpTextMsgKey,
            String iconCssClass) {
        this.authExecId = authExecId;
        this.displayName = displayNameMsgKey;
        this.helpText = helpTextMsgKey;
        this.iconCssClass = iconCssClass;
    }

    public String getAuthExecId() {
        return authExecId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getHelpText() {
        return helpText;
    }

    public String getIconCssClass() {
        return iconCssClass;
    }
}
