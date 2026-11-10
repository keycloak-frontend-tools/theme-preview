package org.keycloak.ext.theme;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.keycloak.WebAuthnConstants;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.services.messages.Messages;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.resource.RealmResourceProvider;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class ThemePreviewProvider implements RealmResourceProvider {

    private static final Pattern BODY_CLOSE = Pattern.compile("(?i)</body>");

    /** Shared with the hub page {@link #list()} (under {@code :root}). */
    private static final String PREVIEW_PALETTE_CSS =
            """
                          :root {
                            --bg: #e8ecf4;
                            --card: #ffffff;
                            --text: #1a1d26;
                            --muted: #5c6370;
                            --border: #c5cad6;
                            --accent: #3d5a9e;
                            --accent-soft: rgba(61, 90, 158, 0.15);
                            --shadow: 0 12px 40px rgba(26, 29, 38, 0.08);
                          }
            """;

    private KeycloakSession session;

    private final List<Page> pages = List.of(
            // Login pages
            new Page("login", "Login - username and password", LoginFormsProvider::createLoginUsernamePassword),
            // Same Keycloak API + login.ftl as "login"; mocked IdPs are injected via SocialLoginRealmProxyInvocationHandler in view().
            new Page("login-social", "Login - username, password, and social IdPs", l -> l.createLoginUsernamePassword()),
            new Page("login-username", "Login - username only", LoginFormsProvider::createLoginUsername),
            new Page("login-password", "Login - password only", LoginFormsProvider::createLoginPassword),
            new Page("login-otp", "Login - OTP", LoginFormsProvider::createLoginTotp),
            new Page("login-webauthn", "Login - WebAuthn", l -> {
                byte[] rawChallenge = new byte[32];
                ThreadLocalRandom.current().nextBytes(rawChallenge);
                String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(rawChallenge);
                l.setAttribute(WebAuthnConstants.CHALLENGE, challenge);
                l.setAttribute(WebAuthnConstants.RP_ID, "localhost");
                l.setAttribute(WebAuthnConstants.USER_VERIFICATION, "preferred");
                l.setAttribute(WebAuthnConstants.IS_USER_IDENTIFIED, Boolean.FALSE.toString());
                // keycloak.v2 webauthn-authenticate.ftl: ${createTimeout?c} (see WebAuthnAuthenticator.fillContextForm)
                l.setAttribute(
                        WebAuthnConstants.CREATE_TIMEOUT,
                        session.getContext().getRealm().getWebAuthnPolicy().getCreateTimeout());
                return l.createLoginWebAuthn();
            }),
            new Page("login-recovery-codes", "Login - recovery codes input", LoginFormsProvider::createLoginRecoveryAuthnCode),
            new Page("login-select-authenticator", "Login - select authenticator", l -> {
                l.setAuthContext(PreviewAuthenticationFlowContext.create(session));
                return l.createSelectAuthenticator();
            }),
            new Page("login-expired", "Login - session expired", LoginFormsProvider::createLoginExpiredPage),
            new Page("login-reset-password", "Login - reset password", LoginFormsProvider::createPasswordReset),
            new Page("login-reset-otp", "Login - reset OTP", LoginFormsProvider::createOtpReset),

            // Registration
            new Page("register", "Registration", l -> {
                l.setFormData(new MultivaluedHashMap<>());
                l.setAttribute("email", "john.doe@example.com");
                return l.createRegistration();
            }),

            // Required actions
            new Page("action-config-totp", "Action - configure OTP", l -> l.createResponse(UserModel.RequiredAction.CONFIGURE_TOTP)),
            new Page("action-config-recovery-codes", "Action - configure recovery codes", l -> {
                l.setAttribute("recoveryAuthnCodesConfigBean", new RecoveryCodesBean());
                return l.createResponse(UserModel.RequiredAction.CONFIGURE_RECOVERY_AUTHN_CODES);
            }),
            new Page("action-update-password", "Action - update password", l -> l.createResponse(UserModel.RequiredAction.UPDATE_PASSWORD)),
            new Page("action-update-profile", "Action - update profile", l -> l.createResponse(UserModel.RequiredAction.UPDATE_PROFILE)),
            new Page("action-verify-email", "Action - verify email", l -> l.createResponse(UserModel.RequiredAction.VERIFY_EMAIL)),
            new Page("action-update-email", "Action - update email", l -> l.createResponse(UserModel.RequiredAction.UPDATE_EMAIL)),
            new Page("action-terms", "Action - terms and conditions", l -> l.createForm("terms.ftl")),

            // IDP linking - these require BrokeredIdentityContext which is complex to mock
            // new Page("idp-link-confirm", "IDP - link confirm", LoginFormsProvider::createIdpLinkConfirmLinkPage),
            // new Page("idp-link-email", "IDP - link email", LoginFormsProvider::createIdpLinkEmailPage),

            // OAuth/Consent
            new Page("oauth-grant", "OAuth - consent/grant", l -> {
                l.setClientSessionCode("preview-session-code");
                l.setAccessRequest(List.of());
                return l.createOAuthGrant();
            }),
            new Page("oauth-device-verify", "OAuth - device verification", LoginFormsProvider::createOAuth2DeviceVerifyUserCodePage),

            // Logout
            new Page("logout-confirm", "Logout - confirmation", l -> {
                l.setClientSessionCode("dummy-session-code");
                return l.createLogoutConfirmPage();
            }),
            new Page("frontchannel-logout", "Logout - front channel", l -> {
                PreviewLogoutMocks.installFrontChannelLogoutHandler(session, session.getContext().getRealm());
                return l.createFrontChannelLogoutPage();
            }),

            // Info/Error pages
            new Page("info", "Info page", l -> {
                l.setInfo(Messages.EXPIRED_CODE);
                return l.createInfoPage();
            }),
            new Page("error", "Error page", l -> {
                l.setError(Messages.INVALID_REQUEST);
                return l.createErrorPage(Response.Status.BAD_REQUEST);
            })
    );

    public ThemePreviewProvider(KeycloakSession session) {
        this.session = session;
    }

    private void appendPreviewPageOptions(StringBuilder sb, boolean hubPlaceholder, String selectedPageSlug) {
        if (hubPlaceholder) {
            sb.append("                    <option value=\"\" selected>Select a preview page…</option>\n");
        }
        for (Page p : pages) {
            boolean selected = !hubPlaceholder && p.template.equals(selectedPageSlug);
            sb.append("                    <option value=\"%s\"%s>%s</option>\n"
                    .formatted(p.template, selected ? " selected" : "", p.description));
        }
    }

    private String previewSwitcherFragment(String currentTemplate) {
        StringBuilder sb = new StringBuilder();
        sb.append(
                """
                <div id="kc-theme-preview-bar" role="region" aria-label="Theme preview page switcher">
                <style>
                  body.kc-theme-preview-body-offset { padding-top: 3.25rem !important; }
                  #kc-theme-preview-bar {
                    --tp-text: #1a1d26;
                    --tp-muted: #5c6370;
                    --tp-border: #c5cad6;
                    --tp-accent: #3d5a9e;
                    --tp-accent-soft: rgba(61, 90, 158, 0.15);
                    position: fixed;
                    top: 0;
                    left: 0;
                    right: 0;
                    z-index: 2147483000;
                    font-family: system-ui, -apple-system, "Segoe UI", Roboto, Ubuntu, sans-serif;
                    font-size: 0.8125rem;
                    color: var(--tp-text);
                    background: rgba(255, 255, 255, 0.94);
                    backdrop-filter: blur(10px);
                    border-bottom: 1px solid var(--tp-border);
                    box-shadow: 0 4px 18px rgba(26, 29, 38, 0.08);
                  }
                  #kc-theme-preview-bar .kc-tp-inner {
                    display: flex;
                    flex-wrap: wrap;
                    align-items: center;
                    gap: 0.5rem 0.75rem;
                    max-width: 72rem;
                    margin: 0 auto;
                    padding: 0.45rem 1rem;
                  }
                  #kc-theme-preview-bar label {
                    font-weight: 600;
                    color: var(--tp-muted);
                    white-space: nowrap;
                  }
                  #kc-theme-preview-bar select {
                    flex: 1 1 12rem;
                    min-width: 10rem;
                    max-width: 36rem;
                    appearance: none;
                    padding: 0.4rem 2rem 0.4rem 0.65rem;
                    font: inherit;
                    font-size: 0.8125rem;
                    color: var(--tp-text);
                    background-color: #f7f8fb;
                    border: 1px solid var(--tp-border);
                    border-radius: 6px;
                    cursor: pointer;
                    background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%235c6370' d='M3 4.5L6 7.5L9 4.5'/%3E%3C/svg%3E");
                    background-repeat: no-repeat;
                    background-position: right 0.55rem center;
                    transition: border-color 0.15s, box-shadow 0.15s, background-color 0.15s;
                  }
                  #kc-theme-preview-bar select:hover {
                    border-color: #a8b0c2;
                    background-color: #fff;
                  }
                  #kc-theme-preview-bar select:focus { outline: none; }
                  #kc-theme-preview-bar select:focus-visible {
                    border-color: var(--tp-accent);
                    box-shadow: 0 0 0 3px var(--tp-accent-soft);
                  }
                </style>
                <script>document.body.classList.add('kc-theme-preview-body-offset');</script>
                <div class="kc-tp-inner">
                  <label for="kc-theme-preview-select">Preview page</label>
                  <select id="kc-theme-preview-select" onchange="if (this.value) location.assign(this.value)">
                """);
        appendPreviewPageOptions(sb, false, currentTemplate);
        sb.append(
                """
                  </select>
                </div>
                </div>
                """);
        return sb.toString();
    }

    private static String readResponseEntityAsString(Response response) {
        Object entity = response.getEntity();
        if (entity == null) {
            return null;
        }
        if (entity instanceof String s) {
            return s;
        }
        if (entity instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (entity instanceof StreamingOutput so) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                so.write(baos);
                return baos.toString(StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private Response injectPreviewSwitcher(Response response, String currentTemplate) {
        if (response.getStatus() != Response.Status.OK.getStatusCode()) {
            return response;
        }
        MediaType mediaType = response.getMediaType();
        if (mediaType == null || !MediaType.TEXT_HTML_TYPE.isCompatible(mediaType)) {
            return response;
        }
        String html = readResponseEntityAsString(response);
        if (html == null) {
            return response;
        }
        Matcher matcher = BODY_CLOSE.matcher(html);
        int lastBody = -1;
        while (matcher.find()) {
            lastBody = matcher.start();
        }
        if (lastBody < 0) {
            return response;
        }
        String modified =
                html.substring(0, lastBody) + previewSwitcherFragment(currentTemplate) + html.substring(lastBody);
        return Response.status(response.getStatus()).type(mediaType).entity(modified).build();
    }

    @Override
    public Object getResource() {
        return this;
    }

    @GET
    @Path("/")
    @Produces(MediaType.TEXT_HTML)
    public Response list() {
        StringBuilder sb = new StringBuilder();
        sb.append(
                """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Theme preview</title>
                <style>
                """);
        sb.append(PREVIEW_PALETTE_CSS);
        sb.append(
                """
                  * { box-sizing: border-box; }
                  body {
                    margin: 0;
                    min-height: 100vh;
                    font-family: system-ui, -apple-system, "Segoe UI", Roboto, Ubuntu, sans-serif;
                    font-size: 1rem;
                    line-height: 1.5;
                    color: var(--text);
                    background: var(--bg);
                    background-image: radial-gradient(ellipse 120% 80% at 50% -20%, #d4dcf0 0%, transparent 55%);
                  }
                  .wrap {
                    min-height: 100vh;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    padding: 1.5rem;
                  }
                  .card {
                    width: 100%;
                    max-width: 32rem;
                    padding: 1.75rem 1.75rem 1.5rem;
                    background: var(--card);
                    border-radius: 12px;
                    box-shadow: var(--shadow);
                    border: 1px solid rgba(197, 202, 214, 0.6);
                  }
                  h1 {
                    margin: 0 0 0.35rem;
                    font-size: 1.35rem;
                    font-weight: 650;
                    letter-spacing: -0.02em;
                  }
                  .hint {
                    margin: 0 0 1.25rem;
                    font-size: 0.9rem;
                    color: var(--muted);
                  }
                  label {
                    display: block;
                    margin-bottom: 0.45rem;
                    font-size: 0.875rem;
                    font-weight: 600;
                    color: var(--text);
                  }
                  select {
                    width: 100%;
                    appearance: none;
                    padding: 0.65rem 2.25rem 0.65rem 0.85rem;
                    font: inherit;
                    font-size: 0.95rem;
                    color: var(--text);
                    background-color: #f7f8fb;
                    border: 1px solid var(--border);
                    border-radius: 8px;
                    cursor: pointer;
                    background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%235c6370' d='M3 4.5L6 7.5L9 4.5'/%3E%3C/svg%3E");
                    background-repeat: no-repeat;
                    background-position: right 0.75rem center;
                    transition: border-color 0.15s, box-shadow 0.15s, background-color 0.15s;
                  }
                  select:hover {
                    border-color: #a8b0c2;
                    background-color: #fff;
                  }
                  select:focus {
                    outline: none;
                  }
                  select:focus-visible {
                    border-color: var(--accent);
                    box-shadow: 0 0 0 3px var(--accent-soft);
                  }
                </style>
                </head>
                <body>
                <main class="wrap">
                  <div class="card">
                    <h1>Theme preview</h1>
                    <p class="hint">Choose a page to render with the current realm theme.</p>
                    <label for="page-select">Preview page</label>
                    <select id="page-select" onchange="if (this.value) location.assign(this.value)">
                """);
        appendPreviewPageOptions(sb, true, null);
        sb.append(
                """
                    </select>
                  </div>
                </main>
                </body>
                </html>
                """);

        return Response.ok(sb.toString()).build();
    }

    @GET
    @Path("/{template}")
    @Produces(MediaType.TEXT_HTML)
    public Response view(@PathParam("template") String page) {
        InvocationHandler realmHandler = "login-social".equals(page)
                ? new SocialLoginRealmProxyInvocationHandler(session)
                : new RealmProxyInvocationHandler(session);

        RealmModel proxy = (RealmModel) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{RealmModel.class}, realmHandler);
        session.getContext().setRealm(proxy);
        session.getContext().setClient(DummyClientModel.create(proxy));

        LoginFormsProviderCacheEviction.evictLoginFormsProvider(session);
        LoginFormsProvider loginForms = session.getProvider(LoginFormsProvider.class);
        FreeMarkerLoginFormsContextSync.apply(loginForms, session);

        URI actionUri = session.getContext().getUri().getBaseUriBuilder()
                .path("realms")
                .path(session.getContext().getRealm().getName())
                .path("theme-preview")
                .path(page)
                .build();
        loginForms.setActionUri(actionUri);
        loginForms.setAuthenticationSession(new DummyAuthenticationSessionModel(proxy));
        // login-social: must leave user unset. IdentityProviderBean (Keycloak main) treats a non-null user with no
        // federated identities as "narrow to linked IdPs only" and uses an empty alias set, so getForLogin is never
        // called and no social buttons render. Other preview pages still use a dummy user for template placeholders.
        if (!"login-social".equals(page)) {
            loginForms.setUser(new DummyUserModel());
        } else {
            loginForms.setUser(null);
        }

        if ("login-social".equals(page)) {
            loginForms.setAttributeMapper(ThemePreviewIdentityProviderBean.socialAttributeMapper());
        }

        Optional<Page> pageHandler = pages.stream().filter(p -> p.template.equals(page)).findFirst();

        try {
            if (pageHandler.isPresent()) {
                Response rendered = pageHandler.get().function.apply(loginForms);
                return injectPreviewSwitcher(rendered, page);
            } else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } finally {
            session.removeAttribute(org.keycloak.protocol.oidc.FrontChannelLogoutHandler.class.getName());
        }
    }

    @POST
    @Path("/{template}")
    @Produces(MediaType.TEXT_HTML)
    public Response post(@PathParam("template") String page) {
        return view(page);
    }

    @Override
    public void close() {

    }

    public class Page {

        String template;
        String description;
        Function<LoginFormsProvider, Response> function;

        public Page(String template, String description, Function<LoginFormsProvider, Response> function) {
            this.template = template;
            this.description = description;
            this.function = function;
        }
    }

}
