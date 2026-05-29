import { Processor, WorkerHost } from '@nestjs/bullmq';
import { Job } from 'bullmq';
import { Logger } from '@nestjs/common';

@Processor('automation-jobs')
export class AutomationProcessor extends WorkerHost {
  private readonly logger = new Logger(AutomationProcessor.name);

  async process(job: Job<any, any, string>): Promise<any> {
    this.logger.log(`Processing job ${job.id} for Applet ${job.data.appletId}`);
    
    // Simulate IFTTT action execution (e.g., hitting an external API)
    await new Promise((resolve) => setTimeout(resolve, 2000));
    
    this.logger.log(`Successfully completed action for Applet ${job.data.appletId}`);
    return { success: true };
  }
}
