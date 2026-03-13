import { UserManager, WebStorageStateStore, User } from 'oidc-client-ts';

export class AuthService {
  private userManager: UserManager | null = null;
  private currentBankId: string | null = null;

  private createUserManager(bankId: string, issuerUrl: string, clientId?: string): UserManager {
    if (!clientId) {
      throw new Error("clientId is required for AuthService initialization");
    }
    const settings = {
      authority: issuerUrl,
      client_id: clientId,
      redirect_uri: `${window.location.origin}/auth/callback`,
      response_type: 'code',
      scope: 'openid profile offline_access api://bank-engine-api/access_as_user',
      post_logout_redirect_uri: window.location.origin,
      userStore: new WebStorageStateStore({ store: window.localStorage }),
      automaticSilentRenew: true,
      loadUserInfo: true
    };
    return new UserManager(settings);
  }

  public async init(bankId: string, issuerUrl: string, clientId?: string) {
    this.currentBankId = bankId;
    this.userManager = this.createUserManager(bankId, issuerUrl, clientId);
  }

  public async login() {
    if (!this.userManager) throw new Error("AuthService not initialized");
    await this.userManager.signinRedirect();
  }

  public async handleCallback(): Promise<User | null> {
    const bankId = localStorage.getItem('plexus_bank_id');
    const issuerUrl = localStorage.getItem('plexus_issuer_url');
    const clientId = localStorage.getItem('plexus_client_id') || undefined;

    if (bankId && issuerUrl) {
      this.userManager = this.createUserManager(bankId, issuerUrl, clientId);
      return await this.userManager.signinRedirectCallback();
    }
    return null;
  }

  public async getUser(): Promise<User | null> {
    const bankId = localStorage.getItem('plexus_bank_id');
    const issuerUrl = localStorage.getItem('plexus_issuer_url');
    const clientId = localStorage.getItem('plexus_client_id') || undefined;

    if (bankId && issuerUrl && !this.userManager) {
      this.userManager = this.createUserManager(bankId, issuerUrl, clientId);
    }

    if (this.userManager) {
      return await this.userManager.getUser();
    }
    return null;
  }

  public async logout() {
    if (this.userManager) {
      await this.userManager.signoutRedirect();
      localStorage.removeItem('plexus_bank_id');
      localStorage.removeItem('plexus_issuer_url');
      localStorage.removeItem('plexus_client_id');
    }
  }
}

export const authService = new AuthService();
