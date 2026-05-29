import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { ConnectedAccount } from './entities/connected-account.entity';
import { EncryptionService } from './encryption.service';
import { User } from '../users/entities/user.entity';

@Injectable()
export class IntegrationsService {
  constructor(
    @InjectRepository(ConnectedAccount)
    private connectedAccountRepository: Repository<ConnectedAccount>,
    private encryptionService: EncryptionService,
  ) {}

  async upsertConnection(user: User, provider: string, providerAccountId: string, accessToken: string, refreshToken?: string) {
    let account = await this.connectedAccountRepository.findOne({ where: { user: { id: user.id }, provider } });
    
    if (!account) {
      account = this.connectedAccountRepository.create({
        user,
        provider,
        providerAccountId,
      });
    } else {
      account.providerAccountId = providerAccountId;
    }

    account.accessToken = this.encryptionService.encrypt(accessToken);
    if (refreshToken) {
      account.refreshToken = this.encryptionService.encrypt(refreshToken);
    }

    return this.connectedAccountRepository.save(account);
  }
}
