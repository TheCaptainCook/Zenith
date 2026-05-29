import { Entity, Column, PrimaryGeneratedColumn, CreateDateColumn, ManyToOne } from 'typeorm';
import { Applet } from './applet.entity';

@Entity('execution_logs')
export class ExecutionLog {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @ManyToOne(() => Applet, applet => applet.id, { onDelete: 'CASCADE' })
  applet: Applet;

  @Column()
  status: string;

  @Column({ type: 'text', nullable: true })
  errorMessage: string;

  @CreateDateColumn()
  executedAt: Date;
}
