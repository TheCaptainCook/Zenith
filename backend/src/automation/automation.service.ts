import { Injectable, Logger } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Applet } from './entities/applet.entity';
import { ExecutionLog } from './entities/execution-log.entity';
import { InjectQueue } from '@nestjs/bullmq';
import { Queue } from 'bullmq';

@Injectable()
export class AutomationService {
  private readonly logger = new Logger(AutomationService.name);

  constructor(
    @InjectRepository(Applet)
    private appletsRepository: Repository<Applet>,
    @InjectRepository(ExecutionLog)
    private logsRepository: Repository<ExecutionLog>,
    @InjectQueue('automation-jobs') private automationQueue: Queue,
  ) {}

  async createApplet(user: any, name: string, triggerConfig: any, actionConfig: any): Promise<Applet> {
    const applet = this.appletsRepository.create({
      name,
      triggerConfig,
      actionConfig,
      user: { id: user.userId }, // Passport JWT Strategy extracts `userId`
    });
    return this.appletsRepository.save(applet);
  }

  async triggerApplet(appletId: string, payload: any) {
    this.logger.log(`Dispatching Applet ${appletId} to the queue`);
    await this.automationQueue.add('execute-action', {
      appletId,
      payload,
    });
    return { message: 'Job queued successfully' };
  }

  async getLogs(userId: string): Promise<ExecutionLog[]> {
    return this.logsRepository.find({
      where: { applet: { user: { id: userId } } },
      relations: { applet: true },
      order: { executedAt: 'DESC' },
      take: 50,
    });
  }
}
