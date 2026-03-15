import axios from 'axios';

export interface UserProfile {
  name: string;
  email: string;
  roles: string[];
  bank_id: string;
  sub: string;
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
      return response.data;
    } catch (err) {
      return null;
    }
  }

  public async logout() {
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = '/logout';

    const csrfToken = this.getCsrfToken();
    if (csrfToken) {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = '_csrf';
        input.value = csrfToken;
        form.appendChild(input);
    }

    document.body.appendChild(form);
    form.submit();
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
