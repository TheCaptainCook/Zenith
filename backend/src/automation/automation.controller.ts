import { Controller, Post, Body, Param, UseGuards, Request, Get } from '@nestjs/common';
import { AutomationService } from './automation.service';
import { AuthGuard } from '@nestjs/passport';

@Controller('automations')
export class AutomationController {
  constructor(private readonly automationService: AutomationService) {}

  @UseGuards(AuthGuard('jwt'))
  @Post()
  async createApplet(@Request() req: any, @Body() body: any) {
    return this.automationService.createApplet(
      req.user,
      body.name,
      body.triggerConfig,
      body.actionConfig,
    );
  }

  @UseGuards(AuthGuard('jwt'))
  @Get('logs')
  async getLogs(@Request() req: any) {
    return this.automationService.getLogs(req.user.userId);
  }

  // This endpoint simulates a webhook triggering an automation
  @Post(':id/trigger')
  async triggerApplet(@Param('id') id: string, @Body() payload: any) {
    return this.automationService.triggerApplet(id, payload);
  }
}
