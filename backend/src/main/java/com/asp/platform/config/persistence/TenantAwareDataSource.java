package com.asp.platform.config.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.asp.platform.identity.TokenClaims;

/// Sets the PostgreSQL session setting `app.tenant_id` on every connection handed out, taken from the
/// signed-in user's token. Row-level security policies then only ever see that workshop's rows, and new
/// rows get it as their `tenant_id` default. With no user signed in the setting is cleared and tenant
/// tables return nothing.
class TenantAwareDataSource extends DelegatingDataSource {

    private static final String BIND_TENANT = "select set_config('app.tenant_id', ?, false)";

    TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return bindTenant(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return bindTenant(super.getConnection(username, password));
    }

    private static Connection bindTenant(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(BIND_TENANT)) {
            statement.setString(1, currentTenant());
            statement.execute();
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    private static String currentTenant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String tenant = jwt.getClaimAsString(TokenClaims.TENANT_ID);
            return tenant == null ? "" : tenant;
        }
        return "";
    }
}
