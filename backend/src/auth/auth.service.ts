import { Injectable, UnauthorizedException, ConflictException } from '@nestjs/common';
import { UsersService } from '../users/users.service';
import { JwtService } from '@nestjs/jwt';
import * as argon2 from 'argon2';

@Injectable()
export class AuthService {
  constructor(
    private usersService: UsersService,
    private jwtService: JwtService
  ) {}

  async validateUser(email: string, pass: string): Promise<any> {
    const user = await this.usersService.findOneByEmail(email);
    if (user && await argon2.verify(user.passwordHash, pass)) {
      const { passwordHash, ...result } = user;
      return result;
    }
    return null;
  }

  async login(user: any) {
    const payload = { email: user.email, sub: user.id, builderPreference: user.builderPreference };
    return {
      access_token: this.jwtService.sign(payload),
    };
  }

  async register(email: string, pass: string, builderPreference: string = 'canvas') {
    const existingUser = await this.usersService.findOneByEmail(email);
    if (existingUser) {
      throw new ConflictException('User already exists');
    }
    const hash = await argon2.hash(pass);
    const newUser = await this.usersService.create(email, hash, builderPreference);
    
    // Auto-login after registration
    return this.login(newUser);
  }
}
