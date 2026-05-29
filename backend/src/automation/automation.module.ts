import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Applet } from './entities/applet.entity';
import { ExecutionLog } from './entities/execution-log.entity';
import { QueueModule } from '../queue/queue.module';
import { AutomationService } from './automation.service';
import { AutomationController } from './automation.controller';

@Module({
  imports: [TypeOrmModule.forFeature([Applet, ExecutionLog]), QueueModule],
  providers: [AutomationService],
  controllers: [AutomationController],
  exports: [AutomationService],
})
export class AutomationModule {}
