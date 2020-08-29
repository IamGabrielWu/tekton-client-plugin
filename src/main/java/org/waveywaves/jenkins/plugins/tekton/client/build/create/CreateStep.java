package org.waveywaves.jenkins.plugins.tekton.client.build.create;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.tekton.client.TektonClient;
import io.fabric8.tekton.pipeline.v1beta1.*;
import io.fabric8.tekton.resource.v1alpha1.DoneablePipelineResource;
import io.fabric8.tekton.resource.v1alpha1.PipelineResource;
import io.fabric8.tekton.resource.v1alpha1.PipelineResourceList;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.waveywaves.jenkins.plugins.tekton.client.TektonUtils;
import org.waveywaves.jenkins.plugins.tekton.client.TektonUtils.TektonResourceType;
import org.waveywaves.jenkins.plugins.tekton.client.build.BaseStep;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Pipe;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class CreateStep extends BaseStep {
    private static final Logger logger = Logger.getLogger(CreateStep.class.getName());

    private String input;
    private String inputType;

    private MixedOperation<TaskRun, TaskRunList, DoneableTaskRun, Resource<TaskRun, DoneableTaskRun>>
            taskRunClient;
    private MixedOperation<Task, TaskList, DoneableTask, Resource<Task, DoneableTask>>
            taskClient;
    private MixedOperation<Pipeline, PipelineList, DoneablePipeline, Resource<Pipeline, DoneablePipeline>>
            pipelineClient;
    private MixedOperation<PipelineRun, PipelineRunList, DoneablePipelineRun, Resource<PipelineRun, DoneablePipelineRun>>
            pipelineRunClient;
    private MixedOperation<PipelineResource, PipelineResourceList, DoneablePipelineResource, Resource<PipelineResource, DoneablePipelineResource>>
            pipelineResourceClient;

    protected enum InputType {
        URL,
        YAML,
        Interactive
    }

    @DataBoundConstructor
    public CreateStep(String input, String inputType) {
        super();
        this.inputType = inputType;
        this.input = input;

        setTektonClient(TektonUtils.getTektonClient());
    }

    protected String getInput(){
        return this.input;
    }
    protected String getInputType(){
        return this.inputType;
    }

    protected String createWithResourceSpecificClient(TektonResourceType resourceType, InputStream inputStream) {
        switch (resourceType) {
            case task:
                return createTask(inputStream);
            case taskrun:
                return createTaskRun(inputStream);
            case pipeline:
                return createPipeline(inputStream);
            case pipelinerun:
                return createPipelineRun(inputStream);
            case pipelineresource:
                return createPipelineResource(inputStream);
            default:
                return "";
        }
    }

    public void setTaskRunClient(
            MixedOperation<TaskRun, TaskRunList, DoneableTaskRun, Resource<TaskRun, DoneableTaskRun>> trc){
        this.taskRunClient = trc;
    }

    public void setTaskClient(
            MixedOperation<Task, TaskList, DoneableTask, Resource<Task, DoneableTask>> tc){
        this.taskClient = tc;
    }

    public void setPipelineClient(
            MixedOperation<Pipeline, PipelineList, DoneablePipeline, Resource<Pipeline, DoneablePipeline>> pc){
        this.pipelineClient = pc;
    }

    public void setPipelineRunClient(
            MixedOperation<PipelineRun, PipelineRunList, DoneablePipelineRun, Resource<PipelineRun, DoneablePipelineRun>> prc){
        this.pipelineRunClient = prc;
    }

    public void setPipelineResourceClient(
            MixedOperation<PipelineResource, PipelineResourceList, DoneablePipelineResource, Resource<PipelineResource, DoneablePipelineResource>> presc){
        this.pipelineResourceClient = presc;
    }

    public String createTaskRun(InputStream inputStream) {
        if (taskRunClient == null) {
            TektonClient tc = (TektonClient) tektonClient;
            setTaskRunClient(tc.v1beta1().taskRuns());
        }
        String resourceName;
        TaskRun taskrun = taskRunClient.load(inputStream).get();
        taskrun = taskRunClient.create(taskrun);
        resourceName = taskrun.getMetadata().getName();
        return resourceName;
    }

    public String createTask(InputStream inputStream) {
        if (taskClient == null) {
            TektonClient tc = (TektonClient) tektonClient;
            setTaskClient(tc.v1beta1().tasks());
        }
        String resourceName;
        Task task = taskClient.load(inputStream).get();
        task = taskClient.create(task);
        resourceName = task.getMetadata().getName();
        return resourceName;
    }

    public String createPipeline(InputStream inputStream) {
        if (pipelineClient == null) {
            TektonClient tc = (TektonClient) tektonClient;
            setPipelineClient(tc.v1beta1().pipelines());
        }
        String resourceName;
        Pipeline pipeline = pipelineClient.load(inputStream).get();
        pipeline = pipelineClient.create(pipeline);
        resourceName = pipeline.getMetadata().getName();
        return resourceName;
    }

    public String createPipelineRun(InputStream inputStream) {
        if (pipelineRunClient == null) {
            TektonClient tc = (TektonClient) tektonClient;
            setPipelineRunClient(tc.v1beta1().pipelineRuns());
        }
        String resourceName;
        PipelineRun pipelineRun = pipelineRunClient.load(inputStream).get();
        pipelineRun = pipelineRunClient.create(pipelineRun);
        resourceName = pipelineRun.getMetadata().getName();
        return resourceName;
    }

    public String createPipelineResource(InputStream inputStream) {
        if (pipelineResourceClient == null) {
            TektonClient tc = (TektonClient) tektonClient;
            setPipelineResourceClient(tc.v1alpha1().pipelineResources());
        }
        String resourceName;
        PipelineResource pipelineRes = pipelineResourceClient.load(inputStream).get();
        pipelineRes = pipelineResourceClient.create(pipelineRes);
        resourceName = pipelineRes.getMetadata().getName();
        return resourceName;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        runCreate();
    }

    protected String runCreate() {
        URL url = null;
        InputStream inputStreamForKind = null;
        InputStream inputStreamForData = null;
        String inputData = this.getInput();
        String inputType = this.getInputType();
        String createdResourceName = "";
        try {
            if (inputType.equals(InputType.URL.toString())) {
                url = new URL(inputData);
                inputStreamForKind = TektonUtils.urlToByteArrayStream(url);
                inputStreamForData = url.openStream();

            } else if (inputType.equals(InputType.YAML.toString())) {
                inputStreamForKind = new ByteArrayInputStream(inputData.getBytes(StandardCharsets.UTF_8));
                inputStreamForData = new ByteArrayInputStream(inputData.getBytes(StandardCharsets.UTF_8));
            }
            if (inputStreamForKind != null) {
                List<TektonResourceType> kind = TektonUtils.getKindFromInputStream(inputStreamForKind, this.getInputType());
                if (kind.size() > 1){
                    logger.info("Multiple Objects in YAML not supported yet");
                } else {
                    createdResourceName = createWithResourceSpecificClient(kind.get(0), inputStreamForData);
                }
            }
        } catch (Exception e) {
            logger.warning("possible URL related Exception has occurred "+e.toString());
        } finally {
            if (inputStreamForKind != null) {
                try {
                    inputStreamForKind.close();
                } catch (IOException e) {
                    logger.warning("IOException occurred "+e.toString());
                }
            }
            if (inputStreamForData != null) {
                try {
                    inputStreamForData.close();
                } catch (IOException e) {
                    logger.warning("IOException occurred "+e.toString());
                }
            }
        }
        return createdResourceName;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public FormValidation doCheckInput(@QueryParameter(value = "input") final String input){
            if (input.length() == 0){
                return FormValidation.error("Input not provided");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillInputTypeItems(@QueryParameter(value = "input") final String input){
            ListBoxModel items =  new ListBoxModel();
            items.add(InputType.URL.toString());
            items.add(InputType.YAML.toString());
            return items;
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Tekton : Create Resource";
        }
    }
}
