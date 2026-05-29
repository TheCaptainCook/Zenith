import { Injectable } from '@nestjs/common';
import { Encryption } from '@boringnode/encryption';
import { chacha20poly1305 } from '@boringnode/encryption/drivers/chacha20_poly1305';

@Injectable()
export class EncryptionService {
  private encryption: Encryption;

  constructor() {
    const appKey = process.env.ENCRYPTION_KEY || '12345678901234567890123456789012'; 
    
    this.encryption = new Encryption(
      chacha20poly1305({
        id: 'zenith_app',
        keys: [appKey],
      })
    );
  }

  encrypt(value: string): string {
    return this.encryption.encrypt(value) as string;
  }

  decrypt(encryptedValue: string): string {
    return this.encryption.decrypt(encryptedValue) as string;
  }
}
