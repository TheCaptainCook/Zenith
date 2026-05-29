import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { User } from './entities/user.entity';

@Injectable()
export class UsersService {
  constructor(
    @InjectRepository(User)
    private usersRepository: Repository<User>,
  ) {}

  async findOneByEmail(email: string): Promise<User | null> {
    return this.usersRepository.findOneBy({ email });
  }

  async create(email: string, passwordHash: string, builderPreference: string = 'canvas'): Promise<User> {
    const user = this.usersRepository.create({ email, passwordHash, builderPreference });
    return this.usersRepository.save(user);
  }
}
