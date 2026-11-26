# API Documentation

## auth-service (port 8081)

Errors: `{"message": "..."}`; validation errors add `"errors": {field: message}`.

| Method | Path | Auth | Body | Success |
|---|---|---|---|---|
| POST | `/api/v1/auth/register` | – | firstName, lastName, email, password (8–72) | 201 UserResponse |
| POST | `/api/v1/auth/login` | – | email, password | 200 TokenResponse |
| POST | `/api/v1/auth/refresh` | – | refreshToken | 200 TokenResponse (token rotated) |
| POST | `/api/v1/auth/logout` | – | refreshToken | 204 |
| GET | `/api/v1/auth/me` | Bearer | – | 200 UserResponse |
| GET | `/.well-known/jwks.json` | – | – | public RSA key (JWKS) |

Statuses: 400 validation, 401 bad credentials / invalid refresh token, 409 email exists.

**TokenResponse**: `accessToken` (JWT RS256, 15 min; claims `sub`=user id, `email`, `role`, `iss`, `jti`),
`refreshToken` (opaque, 7 days, single use, stored as SHA-256), `tokenType` = `Bearer`, `expiresIn` (seconds).
Re-using a rotated refresh token revokes all of that user's refresh tokens.

Config: `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` (PEM). If unset an ephemeral dev key is generated.
