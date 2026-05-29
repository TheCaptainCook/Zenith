import { Entity, Column, PrimaryGeneratedColumn, CreateDateColumn, ManyToOne } from 'typeorm';
import { User } from '../../users/entities/user.entity';

@Entity('applets')
export class Applet {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column()
  name: string;

  @Column({ default: true })
  isActive: boolean;

  // e.g. { provider: 'github', event: 'push', repo: 'my-repo' }
  @Column({ type: 'jsonb' })
  triggerConfig: any;

  // e.g. { provider: 'slack', channel: '#dev', message: 'New commit!' }
  @Column({ type: 'jsonb' })
  actionConfig: any;

  @ManyToOne(() => User)
  user: User;

  @CreateDateColumn()
  createdAt: Date;
}
