package com.sumologic.jenkins.jenkinssumologicplugin.pluginextension.helper;

import com.cloudbees.workflow.rest.external.ErrorExt;
import com.cloudbees.workflow.rest.external.FlowNodeExt;
import com.cloudbees.workflow.rest.external.StageNodeExt;
import com.cloudbees.workflow.rest.external.StatusExt;
import com.sumologic.jenkins.jenkinssumologicplugin.model.ErrorModel;
import com.sumologic.jenkins.jenkinssumologicplugin.model.PipelineStageModel;
import hudson.Extension;
import hudson.model.Result;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.LabelledChunkFinder;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Sumo Logic plugin for Jenkins model.
 *
 *
 *
 * Created by Sourabh Jain on 5/2019.
 */
@Extension
public class PipelineStageExtractor extends SumoPipelineJobIdentifier<WorkflowRun> {

    private static final Logger LOG = Logger.getLogger(PipelineStageExtractor.class.getName());

    @Override
    public List<PipelineStageModel> extractPipelineStages(WorkflowRun workflowRun) {
        FlowExecution flowExecution = workflowRun.getExecution();
        if (flowExecution != null) {
            NodeDetailsExtractor visitor = new NodeDetailsExtractor(workflowRun);
            ForkScanner.visitSimpleChunks(flowExecution.getCurrentHeads(), visitor, new LabelledChunkFinder());
            return createStages(visitor);
        }
        return new ArrayList<>();
    }

    private List<PipelineStageModel> createStages(NodeDetailsExtractor visitor){
        List<PipelineStageModel> stages = new ArrayList<>();

        Collection<StageNodeExt> stageNodes = visitor.getStages();
        Map<String, String> workspaceNodes = visitor.getWorkspaceNodes();
        Map<String, String> parallelNodes = visitor.getParallelNodes();
        LOG.info("Workspace Nodes "+workspaceNodes);
        LOG.info("Parallel Nodes "+parallelNodes);
        if(CollectionUtils.isNotEmpty(stageNodes)){
            stageNodes.forEach(stageNodeExt -> {
                PipelineStageModel stage = getNodeDetails(stageNodeExt, workspaceNodes);
                List<PipelineStageModel> steps = new ArrayList<>();
                stageNodeExt.getStageFlowNodes().forEach(atomFlowNodeExt -> steps.add(getNodeDetails(atomFlowNodeExt, workspaceNodes)));
                if(CollectionUtils.isNotEmpty(steps)){
                    stage.setSteps(steps);
                    if (parallelNodes.containsKey(stageNodeExt.getId())) {
                        stage.setParallelStage(parallelNodes.get(stageNodeExt.getId()));
                    }
                }
                stages.add(stage);
            });
        }
        return stages;
    }

    private PipelineStageModel getNodeDetails(FlowNodeExt stageNodeExt, Map<String, String> workspaceNodes){
        final PipelineStageModel pipelineStageDTO = new PipelineStageModel();
        pipelineStageDTO.setId(stageNodeExt.getId());
        pipelineStageDTO.setName(stageNodeExt.getName());
        pipelineStageDTO.setStatus(convertToResult(stageNodeExt.getStatus()));
        pipelineStageDTO.setStartTime(stageNodeExt.getStartTimeMillis() / 1000f);
        pipelineStageDTO.setDuration(stageNodeExt.getDurationMillis() / 1000f);
        pipelineStageDTO.setPauseDuration(stageNodeExt.getPauseDurationMillis() / 1000f);
        pipelineStageDTO.setArguments(stageNodeExt.getParameterDescription());

        String execNodeName = stageNodeExt.getExecNode();
        if (StringUtils.isEmpty(execNodeName)) {
            //lookup the workspace nodes
            execNodeName = workspaceNodes.get(stageNodeExt.getId());
        }
        pipelineStageDTO.setExecutionNode(execNodeName);

        ErrorExt error = stageNodeExt.getError();
        if (error != null) {
            ErrorModel errorModel = new ErrorModel();
            errorModel.setErrorMessage(error.getMessage());
            errorModel.setErrorType(error.getType());
            pipelineStageDTO.setErrorModel(errorModel);
        }
        return pipelineStageDTO;
    }

    private String convertToResult(StatusExt status) {
        if (status == null) {
            return "UNKNOWN";
        }
        switch (status) {
            case FAILED:
                return Result.FAILURE.toString();
            case NOT_EXECUTED:
                return Result.NOT_BUILT.toString();
            default:
                return status.toString();
        }
    }
}