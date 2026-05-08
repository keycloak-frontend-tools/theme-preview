package org.keycloak.ext.theme;

import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.FrontChannelLogoutHandler;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Installs a {@link FrontChannelLogoutHandler} on the session so
 * {@link org.keycloak.forms.login.freemarker.model.FrontChannelLogoutBean} can render {@code frontchannel-logout.ftl}.
 */
final class PreviewLogoutMocks {

    private PreviewLogoutMocks() {
    }

    static void installFrontChannelLogoutHandler(KeycloakSession session, RealmModel realm) {
        session.removeAttribute(FrontChannelLogoutHandler.class.getName());
        try {
            UserSessionModel userSession = userSessionProxy();
            AuthenticatedClientSessionModel clientSession = authenticatedClientSessionProxy(userSession);
            Constructor<FrontChannelLogoutHandler> ctor = FrontChannelLogoutHandler.class.getDeclaredConstructor(
                    KeycloakSession.class, AuthenticatedClientSessionModel.class);
            ctor.setAccessible(true);
            FrontChannelLogoutHandler handler = ctor.newInstance(session, clientSession);
            handler.addClient(DummyClientModel.create(realm));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not install FrontChannelLogoutHandler for theme preview", e);
        }
    }

    private static UserSessionModel userSessionProxy() {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethods(proxy, method, args);
            }
            if ("getId".equals(method.getName())) {
                return "preview-user-session";
            }
            return defaultValue(method);
        };
        return (UserSessionModel) Proxy.newProxyInstance(
                PreviewLogoutMocks.class.getClassLoader(),
                new Class[]{UserSessionModel.class},
                handler);
    }

    private static AuthenticatedClientSessionModel authenticatedClientSessionProxy(UserSessionModel userSession) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethods(proxy, method, args);
            }
            if ("getUserSession".equals(method.getName())) {
                return userSession;
            }
            if ("getNote".equals(method.getName()) && args != null && args.length > 0
                    && OIDCLoginProtocol.ISSUER.equals(String.valueOf(args[0]))) {
                return "https://preview.example/realms/preview";
            }
            return defaultValue(method);
        };
        return (AuthenticatedClientSessionModel) Proxy.newProxyInstance(
                PreviewLogoutMocks.class.getClassLoader(),
                new Class[]{AuthenticatedClientSessionModel.class},
                handler);
    }

    private static Object handleObjectMethods(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "equals" -> args != null && args.length == 1 && proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "theme-preview.logout-mock";
            default -> null;
        };
    }

    private static Object defaultValue(Method method) {
        Class<?> t = method.getReturnType();
        if (t == void.class) {
            return null;
        }
        if (!t.isPrimitive()) {
            if (t == String.class) {
                return null;
            }
            if (Map.class.isAssignableFrom(t)) {
                return Collections.emptyMap();
            }
            if (Set.class.isAssignableFrom(t)) {
                return Collections.emptySet();
            }
            if (Iterable.class.isAssignableFrom(t)) {
                return Collections.emptyList();
            }
            if (t == Stream.class) {
                return Stream.empty();
            }
        }
        if (t == boolean.class || t == Boolean.class) {
            return false;
        }
        if (t == int.class || t == Integer.class) {
            return 0;
        }
        if (t == long.class || t == Long.class) {
            return 0L;
        }
        return null;
    }
}
