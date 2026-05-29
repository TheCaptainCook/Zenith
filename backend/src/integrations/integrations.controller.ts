import { Controller, Get, Param, Query, Res, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { IntegrationsService } from './integrations.service';
import { UsersService } from '../users/users.service';

@Controller('integrations')
export class IntegrationsController {
  constructor(
    private readonly integrationsService: IntegrationsService,
    private readonly usersService: UsersService,
    private readonly jwtService: JwtService,
  ) {}

  @Get(':provider/auth')
  async startOAuth(@Param('provider') provider: string, @Query('token') token: string, @Res() res: any) {
    if (!token) throw new UnauthorizedException('Token required');
    try {
      this.jwtService.verify(token);
    } catch(e) {
      throw new UnauthorizedException('Invalid token');
    }

    if (provider === 'mockoauth') {
      const callbackUrl = `http://localhost:3000/integrations/mockoauth/callback?state=${token}&code=mock_auth_code_123`;
      return res.redirect(callbackUrl);
    }
    return res.status(404).send('Provider not found');
  }

  @Get(':provider/callback')
  async oauthCallback(@Param('provider') provider: string, @Query('code') code: string, @Query('state') state: string, @Res() res: any) {
    if (!state) throw new UnauthorizedException('State missing');
    
    let decoded;
    try {
      decoded = this.jwtService.verify(state);
    } catch(e) {
      throw new UnauthorizedException('Invalid state');
    }
    
    const user = await this.usersService.findOneByEmail(decoded.email);
    if (!user) throw new UnauthorizedException('User not found');

    if (provider === 'mockoauth') {
      const mockAccessToken = 'mock_access_token_abc123';
      await this.integrationsService.upsertConnection(user, 'mockoauth', 'mock_user_1', mockAccessToken);
      
      return res.send(`
        <html>
          <body style="background:#111827;color:white;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;">
            <div style="text-align:center;">
              <h2 style="color:#10B981;">Mock OAuth Successful!</h2>
              <p>Your account is now connected.</p>
              <button onclick="window.close()" style="background:#4F46E5;color:white;border:none;padding:12px 24px;border-radius:8px;font-size:16px;cursor:pointer;">Return to Zenith</button>
            </div>
          </body>
        </html>
      `);
    }

    return res.status(404).send('Provider not found');
  }
}
