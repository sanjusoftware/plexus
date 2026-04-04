import axios from 'axios';

export interface UserProfile {
  name: string;
  email: string;
  roles: string[];
  bank_id: string;
  bankName: string;
  currencyCode: string;
  sub: string;
  picture?: string;
  permissions: string[];
}

export class AuthService {
  public async login(bankId: string) {
    try {
      // First, check if the bank is valid and active
      await axios.get(`/api/v1/auth/check-bank?bankId=${bankId}`);

      // If successful, redirect to backend login endpoint which initiates OIDC flow
      window.location.href = `/api/v1/auth/login?bankId=${bankId}`;
    } catch (err: any) {
      if (err.response) {
        const { status, data } = err.response;
        const message = data?.message || 'Authentication failed';
        const timestamp = data?.timestamp || new Date().toISOString();
        window.location.href = `/error?status=${status}&message=${encodeURIComponent(message)}&timestamp=${encodeURIComponent(timestamp)}`;
      } else {
        window.location.href = `/error?status=500&message=${encodeURIComponent('Connection error')}`;
      }
    }
  }

  public async getUser(): Promise<UserProfile | null> {
    try {
      const response = await axios.get('/api/v1/auth/user');
      // Ensure we got a JSON response and not an HTML redirect page
      if (response.data && typeof response.data === 'object' && 'sub' in response.data) {
        return response.data;
      }
      return null;
    } catch (err) {
      return null;
    }
  }

  public async getPermissionsMap(): Promise<Record<string, string[]>> {
    try {
      const response = await axios.get('/api/v1/roles/permissions-map');
      return response.data || {};
    } catch (err) {
      console.error('Failed to fetch permissions map', err);
      return {};
    }
  }

  public async logout() {
    try {
      const csrfToken = this.getCsrfToken();
      await axios.post('/logout', null, {
        headers: {
          'X-XSRF-TOKEN': csrfToken || ''
        }
      });
    } catch (err) {
      console.error("Logout request failed", err);
    } finally {
      // Always try to clear cookies manually as a safety measure
      document.cookie = "JSESSIONID=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
      document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
    }
  }

  public getCsrfToken(): string | null {
    const name = "XSRF-TOKEN=";
    const decodedCookie = decodeURIComponent(document.cookie);
    const ca = decodedCookie.split(';');
    for(let i = 0; i < ca.length; i++) {
        let c = ca[i];
        while (c.charAt(0) === ' ') {
            c = c.substring(1);
        }
        if (c.indexOf(name) === 0) {
            return c.substring(name.length, c.length);
        }
    }
    return null;
  }
}

export const authService = new AuthService();