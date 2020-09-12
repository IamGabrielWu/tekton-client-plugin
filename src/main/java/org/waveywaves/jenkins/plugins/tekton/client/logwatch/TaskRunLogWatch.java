package org.waveywaves.jenkins.plugins.tekton.client.logwatch;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.tekton.pipeline.v1beta1.TaskRun;
import org.waveywaves.jenkins.plugins.tekton.client.TektonUtils;
import org.waveywaves.jenkins.plugins.tekton.client.TektonUtils.TektonResourceType;
import org.waveywaves.jenkins.plugins.tekton.client.build.create.CreateStep;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Logger;

public class TaskRunLogWatch implements Runnable{
    private static final Logger logger = Logger.getLogger(TaskRunLogWatch.class.getName());

    private KubernetesClient kubernetesClient;
    private TaskRun taskRun;
    OutputStream consoleLogger;

    public TaskRunLogWatch(KubernetesClient kubernetesClient, TaskRun taskRun, OutputStream consoleLogger) {
        this.kubernetesClient = kubernetesClient;
        this.taskRun = taskRun;
        this.consoleLogger = consoleLogger;
    }

    @Override
    public void run() {
        List<Pod> pods = kubernetesClient.pods().list().getItems();
        Pod taskRunPod = null;
        String podName = "";
        for (Pod pod : pods) {
            List<OwnerReference> ownerReferences = pod.getMetadata().getOwnerReferences();
            if (ownerReferences != null && ownerReferences.size() > 0) {
                for (OwnerReference or : ownerReferences) {
                    String orKind = or.getKind();
                    String orName = or.getName();
                    if (orKind.toLowerCase().equals(TektonResourceType.taskrun.toString())
                            && orName.equals(taskRun.getMetadata().getName())){
                        podName = pod.getMetadata().getName();
                        taskRunPod = pod;
                    }
                }
            }
        }
        try {
            if (!podName.isEmpty() && taskRunPod != null){
                Predicate<Pod> runningState = i -> (i.getStatus().getPhase().equals("Running"));
                PodResource<Pod, DoneablePod> pr = kubernetesClient.pods().inNamespace(taskRunPod.getMetadata().getNamespace()).withName(podName);
                pr.waitUntilCondition(runningState,10, TimeUnit.SECONDS);
                List<String> taskRunContainerNames = new ArrayList<String>();
                for (Container c : taskRunPod.getSpec().getContainers()) {
                    taskRunContainerNames.add(c.getName());
                }
                for (String i : taskRunContainerNames) {
                    pr.inContainer(i).watchLog(this.consoleLogger);
                }
            }
        } catch ( InterruptedException e) {
            logger.warning("Interrupted Exception Occurred");
        }
    }
}
