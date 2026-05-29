import { Module } from '@nestjs/common';
import { BullModule } from '@nestjs/bullmq';
import { AutomationProcessor } from './automation.processor';

@Module({
  imports: [
    BullModule.forRoot({
      connection: {
        host: 'localhost',
        port: 6379,
      },
    }),
    BullModule.registerQueue({
      name: 'automation-jobs',
    }),
  ],
  providers: [AutomationProcessor],
})
export class WorkerModule {}
