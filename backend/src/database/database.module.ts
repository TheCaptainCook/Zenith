import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { User } from '../users/entities/user.entity';
import { Applet } from '../automation/entities/applet.entity';

@Module({
  imports: [
    TypeOrmModule.forRoot({
      type: 'postgres',
      host: 'localhost',
      port: 5432,
      username: 'zenith_admin',
      password: 'zenith_password',
      database: 'zenith_db',
      autoLoadEntities: true,
      synchronize: true, // Auto-create tables (Dev only)
    }),
  ],
})
export class DatabaseModule {}
