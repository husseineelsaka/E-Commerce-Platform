import http from 'k6/http';

// Password grant for the test customer; KEYCLOAK_URL and CUSTOMER_PASSWORD come from the environment.
export function customerToken() {
  const keycloak = __ENV.KEYCLOAK_URL || 'http://localhost:8180';
  const response = http.post(`${keycloak}/realms/ecommerce-platform/protocol/openid-connect/token`, {
    grant_type: 'password',
    client_id: 'user-sign-in',
    username: __ENV.CUSTOMER_USERNAME || 'customer-test',
    password: __ENV.CUSTOMER_PASSWORD,
  });
  if (response.status !== 200) {
    throw new Error(`token request failed: ${response.status}`);
  }
  return response.json('access_token');
}
