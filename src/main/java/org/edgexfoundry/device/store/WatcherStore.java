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
 * @author: Jim White, Dell
 * @version: 1.0.0
 *******************************************************************************/

package org.edgexfoundry.device.store;

import java.util.List;
import java.util.Map;

import org.edgexfoundry.device.domain.configuration.BaseProvisionWatcherConfiguration;
import org.edgexfoundry.domain.meta.ProvisionWatcher;

public interface WatcherStore {

  void setWatchers(Map<String, ProvisionWatcher> watchers);

  Map<String, ProvisionWatcher> getWatchers();

  boolean add(String provisionWatcherId);

  boolean add(ProvisionWatcher watcher);

  boolean remove(String provisionWatcherId);

  boolean remove(ProvisionWatcher provisionWatcher);

  boolean update(String provisionWatcherId);

  boolean update(ProvisionWatcher provisionWatcher);

  void initialize(String deviceServiceId, BaseProvisionWatcherConfiguration configuration);

  List<ProvisionWatcher> getWatcherByProfileName(String profileName);

}
