/*******************************************************************************
 * Copyright 2016-2017 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * @microservice: device-domain
 * @author: Tyler Cox, Dell
 * @version: 1.0.0
 *******************************************************************************/

package org.edgexfoundry.device.store.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.edgexfoundry.controller.DeviceProfileClient;
import org.edgexfoundry.controller.ValueDescriptorClient;
import org.edgexfoundry.device.domain.ServiceObject;
import org.edgexfoundry.device.domain.ServiceObjectFactory;
import org.edgexfoundry.device.store.ProfileStore;
import org.edgexfoundry.domain.common.IoTType;
import org.edgexfoundry.domain.common.ValueDescriptor;
import org.edgexfoundry.domain.meta.Command;
import org.edgexfoundry.domain.meta.Device;
import org.edgexfoundry.domain.meta.DeviceObject;
import org.edgexfoundry.domain.meta.DeviceProfile;
import org.edgexfoundry.domain.meta.ProfileResource;
import org.edgexfoundry.domain.meta.PropertyValue;
import org.edgexfoundry.domain.meta.ResourceOperation;
import org.edgexfoundry.domain.meta.Units;
import org.edgexfoundry.support.logging.client.EdgeXLogger;
import org.edgexfoundry.support.logging.client.EdgeXLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

// TODO - jpw Tyler - what happens if a profile is removed. How does this get cleaned out??
/**
 * Cache of value descriptors, command and objects based on whats in the DS profile
 * 
 * @author Jim White
 *
 */
@Repository
public class ProfileStoreImpl implements ProfileStore {

  private static final EdgeXLogger logger =
      EdgeXLoggerFactory.getEdgeXLogger(ProfileStoreImpl.class);

  @Autowired
  private ValueDescriptorClient valueDescriptorClient;

  @Autowired
  private DeviceProfileClient deviceProfileClient;
  
  @Autowired
  private ServiceObjectFactory serviceObjectFactory;

  private List<ValueDescriptor> valueDescriptors = new ArrayList<>();

  // TODO - jpw - wow, make these simpler, separate objects
  // map (key of device name) to cache of each device's resources keyed by resource name
  // mapped to resource operations arrays keyed by get or put operation
  private Map<String, Map<String, Map<String, List<ResourceOperation>>>> commands = new HashMap<>();

  // map (key of device name) to cache each device's profile objects by profile object key
  private Map<String, Map<String, ServiceObject>> objects = new HashMap<>();

  @Override
  public Map<String, Map<String, Map<String, List<ResourceOperation>>>> getCommands() {
    return commands;
  }

  @Override
  public Map<String, Map<String, ServiceObject>> getObjects() {
    return objects;
  }

  @Override
  public List<ValueDescriptor> getValueDescriptors() {
    return valueDescriptors;
  }

  @Override
  public boolean descriptorExists(String name) {
    return !getValueDescriptors().stream().filter(desc -> desc.getName().equals(name))
        .collect(Collectors.toList()).isEmpty();
  }

  @Override
  public void addDevice(Device device) {
    if (completeProfile(device)) {
      List<ValueDescriptor> descriptors = retrieveAllValueDescriptors();
      List<String> usedDescriptors = retrieveUsedDescriptors(device);

      Map<String, Map<String, List<ResourceOperation>>> deviceOperations = new HashMap<>();
      List<ResourceOperation> ops = new ArrayList<>();
      retreiveOperations(device, deviceOperations, ops);

      Map<String, ServiceObject> deviceObjects = new HashMap<>();
      buildDeviceObjectsMap(device, deviceObjects, deviceOperations, ops);

      objects.put(device.getName(), deviceObjects);
      commands.put(device.getName(), deviceOperations);

      collectValueDescriptors(device, ops, descriptors, usedDescriptors);
    } else {
      logger.error(
          "Device is not associated to a profile and cannot therefore be added to the caches");
    }
  }

  @Override
  public void updateDevice(Device device) {
    removeDevice(device);
    addDevice(device);
  }

  @Override
  public void removeDevice(Device device) {
    objects.remove(device.getName());
    commands.remove(device.getName());
  }

  private ValueDescriptor createDescriptor(String name, DeviceObject object) {
    PropertyValue value = object.getProperties().getValue();
    Units units = object.getProperties().getUnits();
    ValueDescriptor descriptor = new ValueDescriptor(name, value.getMinimum(), value.getMaximum(),
        IoTType.valueOf(value.getType().substring(0, 1)), units.getDefaultValue(),
        value.getDefaultValue(), "%s", null, object.getDescription());
    try {
      descriptor.setId(valueDescriptorClient.add(descriptor));
    } catch (Exception e) {
      logger.error("Adding Value descriptor: " + descriptor.getName() + " failed with error "
          + e.getMessage());
    }
    return descriptor;
  }

  private List<ValueDescriptor> retrieveAllValueDescriptors() {
    List<ValueDescriptor> descriptors;
    try {
      descriptors = valueDescriptorClient.valueDescriptors();
    } catch (Exception e) {
      descriptors = new ArrayList<>();
    }
    return descriptors;
  }

  private List<String> retrieveUsedDescriptors(Device device) {
    List<String> usedDescriptors = new ArrayList<>();
    if (device.getProfile() != null && device.getProfile().getCommands() != null) {
      for (Command command : device.getProfile().getCommands()) {
        usedDescriptors.addAll(command.associatedValueDescriptors());
      }
    }
    return usedDescriptors;
  }

  private boolean completeProfile(Device device) {
    if (device.getProfile() != null) {
      if (device.getProfile().getDeviceResources() == null) {
        DeviceProfile profile =
            deviceProfileClient.deviceProfileForName(device.getProfile().getName());
        device.setProfile(profile);
      }
      return true;
    }
    return false;
  }

  private void retreiveOperations(Device device,
      Map<String, Map<String, List<ResourceOperation>>> deviceOperations,
      List<ResourceOperation> ops) {
    if (device.getProfile() != null && device.getProfile().getResources() != null) {
      for (ProfileResource resource : device.getProfile().getResources()) {
        Map<String, List<ResourceOperation>> operations = new HashMap<>();
        operations.put("get", resource.getGet());
        operations.put("set", resource.getSet());
        deviceOperations.put(resource.getName().toLowerCase(), operations);
        if (resource.getGet() != null) {
          ops.addAll(resource.getGet());
        }
        if (resource.getSet() != null) {
          ops.addAll(resource.getSet());
        }
      }
    }
  }

  private void collectValueDescriptors(Device device, List<ResourceOperation> ops,
      List<ValueDescriptor> descriptors, List<String> usedDescriptors) {
    // Create a value descriptor for each parameter using its underlying object
    for (ResourceOperation op : ops) {
      ValueDescriptor descriptor = descriptors.stream()
          .filter(d -> d.getName().equals(op.getParameter())).findAny().orElse(null);

      if (descriptor == null) {
        if (!usedDescriptors.contains(op.getParameter())) {
          continue;
        }

        DeviceObject object = device.getProfile().getDeviceResources().stream()
            .filter(obj -> obj.getName().equals(op.getObject())).findAny().orElse(null);

        descriptor = createDescriptor(op.getParameter(), object);
      }

      if (!valueDescriptors.contains(descriptor))
        valueDescriptors.add(descriptor);
      descriptors.add(descriptor);
    }
  }

  // TODO - jpw - need to simplify
  private void buildDeviceObjectsMap(Device device, Map<String, ServiceObject> deviceObjects,
      Map<String, Map<String, List<ResourceOperation>>> deviceOperations,
      List<ResourceOperation> ops) {
    // put the device's profile objects in the objects map
    // put the device's profile objects in the commands map if no resource exists
    for (DeviceObject object : device.getProfile().getDeviceResources()) {
      ServiceObject newServiceObject = serviceObjectFactory.createServiceObject(object);

      PropertyValue value = object.getProperties().getValue();

      deviceObjects.put(object.getName(), newServiceObject);

      // if there is no resource defined for an object, create one based on the
      // RW parameters
      if (!deviceOperations.containsKey(object.getName().toLowerCase())) {
        String readWrite = value.getReadWrite();

        Map<String, List<ResourceOperation>> operations = new HashMap<>();

        if (readWrite.toLowerCase().contains("r")) {
          ResourceOperation resource = new ResourceOperation("get", object.getName());
          List<ResourceOperation> getOp = new ArrayList<>();
          getOp.add(resource);
          operations.put(resource.getOperation().toLowerCase(), getOp);
          ops.add(resource);
        }

        if (readWrite.toLowerCase().contains("w")) {
          ResourceOperation resource = new ResourceOperation("set", object.getName());
          List<ResourceOperation> setOp = new ArrayList<>();
          setOp.add(resource);
          operations.put(resource.getOperation().toLowerCase(), setOp);
          ops.add(resource);
        }

        deviceOperations.put(object.getName().toLowerCase(), operations);
      }
    }
  }
}
